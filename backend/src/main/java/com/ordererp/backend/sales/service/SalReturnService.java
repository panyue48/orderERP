package com.ordererp.backend.sales.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.sales.dto.SalReturnCreateRequest;
import com.ordererp.backend.sales.dto.SalReturnDetailResponse;
import com.ordererp.backend.sales.dto.SalReturnExecuteResponse;
import com.ordererp.backend.sales.dto.SalReturnItemResponse;
import com.ordererp.backend.sales.dto.SalReturnLineRequest;
import com.ordererp.backend.sales.dto.SalReturnResponse;
import com.ordererp.backend.sales.entity.SalReturn;
import com.ordererp.backend.sales.entity.SalReturnDetail;
import com.ordererp.backend.sales.repository.SalReturnDetailRepository;
import com.ordererp.backend.sales.repository.SalReturnRepository;
import com.ordererp.backend.wms.entity.WmsIoBill;
import com.ordererp.backend.wms.entity.WmsIoBillDetail;
import com.ordererp.backend.wms.entity.WmsStock;
import com.ordererp.backend.wms.entity.WmsStockLog;
import com.ordererp.backend.wms.repository.WmsIoBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsIoBillRepository;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import com.ordererp.backend.wms.repository.WmsStockRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalReturnService {
    private static final int STATUS_PENDING_AUDIT = 1;
    private static final int STATUS_AUDITED = 2;
    private static final int STATUS_COMPLETED = 4;
    private static final int STATUS_CANCELED = 9;

    private static final int PARTNER_TYPE_CUSTOMER = 2;

    private static final int WMS_BILL_TYPE_SALES_RETURN_IN = 6;
    private static final int WMS_BILL_STATUS_COMPLETED = 2;

    private final SalReturnRepository returnRepository;
    private final SalReturnDetailRepository detailRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockLogRepository stockLogRepository;

    public SalReturnService(SalReturnRepository returnRepository, SalReturnDetailRepository detailRepository,
            BasePartnerRepository partnerRepository, BaseWarehouseRepository warehouseRepository,
            BaseProductRepository productRepository, WmsIoBillRepository ioBillRepository,
            WmsIoBillDetailRepository ioBillDetailRepository, WmsStockRepository stockRepository,
            WmsStockLogRepository stockLogRepository) {
        this.returnRepository = returnRepository;
        this.detailRepository = detailRepository;
        this.partnerRepository = partnerRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockRepository = stockRepository;
        this.stockLogRepository = stockLogRepository;
    }

    public Page<SalReturnResponse> page(String keyword, Pageable pageable) {
        return returnRepository.pageRows(trimToNull(keyword), pageable).map(SalReturnService::toResponse);
    }

    public SalReturnDetailResponse detail(Long id) {
        SalReturnRepository.SalReturnRow row = returnRepository.getRow(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在");
        }
        List<SalReturnItemResponse> items = detailRepository.listRows(id).stream()
                .map(r -> new SalReturnItemResponse(
                        r.getId(),
                        r.getProductId(),
                        r.getProductCode(),
                        r.getProductName(),
                        r.getUnit(),
                        r.getPrice(),
                        r.getQty(),
                        r.getAmount()))
                .toList();
        return new SalReturnDetailResponse(toResponse(row), items);
    }

    @Transactional
    public SalReturnResponse create(SalReturnCreateRequest request, String createdBy) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");

        BasePartner customer = partnerRepository.findByIdAndDeleted(request.customerId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户不存在"));
        if (customer.getType() == null || customer.getType() != PARTNER_TYPE_CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来单位不是客户");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(request.warehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        validateLines(request.lines());

        LocalDateTime now = LocalDateTime.now();
        SalReturn header = new SalReturn();
        header.setReturnNo(generateReturnNo());
        header.setCustomerId(customer.getId());
        header.setCustomerCode(customer.getPartnerCode());
        header.setCustomerName(customer.getPartnerName());
        header.setWarehouseId(wh.getId());
        header.setReturnDate(request.returnDate() == null ? LocalDate.now() : request.returnDate());
        header.setTotalAmount(BigDecimal.ZERO);
        header.setStatus(STATUS_PENDING_AUDIT);
        header.setRemark(trimToNull(request.remark()));
        header.setCreateBy(trimToNull(createdBy));
        header.setCreateTime(now);
        header = returnRepository.saveAndFlush(header);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SalReturnDetail> details = new ArrayList<>();
        for (SalReturnLineRequest line : request.lines()) {
            BaseProduct p = productRepository.findByIdAndDeleted(line.productId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在: " + line.productId()));
            if (p.getStatus() != null && p.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已禁用: " + p.getId());
            }

            BigDecimal qty = safeQty(line.qty());
            BigDecimal price = safeMoney(line.price());
            BigDecimal amount = price.multiply(qty);

            SalReturnDetail d = new SalReturnDetail();
            d.setReturnId(header.getId());
            d.setProductId(p.getId());
            d.setProductCode(p.getProductCode());
            d.setProductName(p.getProductName());
            d.setUnit(p.getUnit());
            d.setPrice(price);
            d.setQty(qty);
            d.setAmount(amount);
            details.add(d);

            totalAmount = totalAmount.add(amount);
        }

        header.setTotalAmount(totalAmount);
        returnRepository.save(header);
        detailRepository.saveAll(details);

        return toResponse(returnRepository.getRow(header.getId()));
    }

    @Transactional
    public SalReturnResponse audit(Long id, String auditBy) {
        SalReturn r = returnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在"));
        if (Objects.equals(r.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已作废");
        }
        if (Objects.equals(r.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已完成");
        }
        if (!Objects.equals(r.getStatus(), STATUS_PENDING_AUDIT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单状态不允许审核");
        }

        r.setStatus(STATUS_AUDITED);
        r.setAuditBy(trimToNull(auditBy));
        r.setAuditTime(LocalDateTime.now());
        returnRepository.save(r);
        return toResponse(returnRepository.getRow(r.getId()));
    }

    /**
     * 执行销售退货：生成 WMS 入库单（type=6）并增加库存。
     *
     * <p>幂等语义：</p>
     * <ul>
     *   <li>同一退货单重复 execute 不会重复加库存：以 (biz_id=returnId, type=6) 唯一约束兜底。</li>
     * </ul>
     */
    @Transactional
    public SalReturnExecuteResponse execute(Long id, String operator) {
        SalReturn r = returnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在"));
        if (Objects.equals(r.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已作废");
        }

        WmsIoBill existing = ioBillRepository.findFirstByBizIdAndType(r.getId(), WMS_BILL_TYPE_SALES_RETURN_IN).orElse(null);
        if (existing != null) {
            if (!Objects.equals(r.getStatus(), STATUS_COMPLETED)) {
                r.setStatus(STATUS_COMPLETED);
                r.setWmsBillId(existing.getId());
                r.setWmsBillNo(existing.getBillNo());
                r.setExecuteBy(trimToNull(operator));
                r.setExecuteTime(LocalDateTime.now());
                returnRepository.save(r);
            }
            return new SalReturnExecuteResponse(r.getId(), r.getReturnNo(), r.getStatus(), existing.getId(), existing.getBillNo());
        }

        if (!Objects.equals(r.getStatus(), STATUS_AUDITED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单未审核，不能执行");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(r.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        List<SalReturnDetail> details = detailRepository.findByReturnIdOrderByIdAsc(r.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单没有明细");
        }

        LocalDateTime now = LocalDateTime.now();
        WmsIoBill bill = new WmsIoBill();
        bill.setBillNo(generateWmsBillNo());
        bill.setType(WMS_BILL_TYPE_SALES_RETURN_IN);
        bill.setBizId(r.getId());
        bill.setBizNo(r.getReturnNo());
        bill.setWarehouseId(wh.getId());
        bill.setStatus(WMS_BILL_STATUS_COMPLETED);
        bill.setRemark("销售退货入库: " + r.getReturnNo());
        bill.setCreateBy(trimToNull(operator));
        bill.setCreateTime(now);
        try {
            bill = ioBillRepository.saveAndFlush(bill);
        } catch (DataIntegrityViolationException e) {
            existing = ioBillRepository.findFirstByBizIdAndType(r.getId(), WMS_BILL_TYPE_SALES_RETURN_IN).orElse(null);
            if (existing != null) {
                return new SalReturnExecuteResponse(r.getId(), r.getReturnNo(), r.getStatus(), existing.getId(), existing.getBillNo());
            }
            throw e;
        }

        for (SalReturnDetail d : details) {
            BigDecimal qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid qty on detail: " + d.getId());
            }

            WmsIoBillDetail bd = new WmsIoBillDetail();
            bd.setBillId(bill.getId());
            bd.setProductId(d.getProductId());
            bd.setQty(qty);
            bd.setRealQty(qty);
            ioBillDetailRepository.save(bd);

            WmsStock stock = increaseStockWithRetry(wh.getId(), d.getProductId(), qty);
            WmsStockLog log = new WmsStockLog();
            log.setWarehouseId(wh.getId());
            log.setProductId(d.getProductId());
            log.setBizType("SALES_RETURN");
            log.setBizNo(bill.getBillNo());
            log.setChangeQty(qty);
            log.setAfterStockQty(safeQty(stock.getStockQty()));
            log.setCreateTime(now);
            stockLogRepository.save(log);
        }

        r.setStatus(STATUS_COMPLETED);
        r.setWmsBillId(bill.getId());
        r.setWmsBillNo(bill.getBillNo());
        r.setExecuteBy(trimToNull(operator));
        r.setExecuteTime(now);
        returnRepository.save(r);
        return new SalReturnExecuteResponse(r.getId(), r.getReturnNo(), r.getStatus(), bill.getId(), bill.getBillNo());
    }

    @Transactional
    public SalReturnResponse cancel(Long id, String operator) {
        SalReturn r = returnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在"));
        if (Objects.equals(r.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已完成，不能作废");
        }
        if (Objects.equals(r.getStatus(), STATUS_CANCELED)) {
            return toResponse(returnRepository.getRow(r.getId()));
        }

        r.setStatus(STATUS_CANCELED);
        r.setRemark(appendRemark(r.getRemark(), "作废: " + trimToNull(operator)));
        returnRepository.save(r);
        return toResponse(returnRepository.getRow(r.getId()));
    }

    private WmsStock increaseStockWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return increaseStockOnce(warehouseId, productId, qty);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private WmsStock increaseStockOnce(Long warehouseId, Long productId, BigDecimal qty) {
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be positive");
        }
        LocalDateTime now = LocalDateTime.now();

        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElse(null);
        if (stock == null) {
            WmsStock created = new WmsStock();
            created.setWarehouseId(warehouseId);
            created.setProductId(productId);
            created.setStockQty(qty);
            created.setLockedQty(BigDecimal.ZERO);
            created.setVersion(0);
            created.setUpdateTime(now);
            validateStockInvariant(created.getStockQty(), created.getLockedQty());
            try {
                return stockRepository.saveAndFlush(created);
            } catch (DataIntegrityViolationException e) {
                stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                        .orElseThrow(() -> e);
            }
        }

        BigDecimal stockQty = safeQty(stock.getStockQty());
        BigDecimal lockedQty = safeQty(stock.getLockedQty());
        validateStockInvariant(stockQty, lockedQty);
        stock.setStockQty(stockQty.add(qty));
        stock.setLockedQty(lockedQty);
        stock.setUpdateTime(now);
        validateStockInvariant(stock.getStockQty(), stock.getLockedQty());
        return stockRepository.saveAndFlush(stock);
    }

    private static void validateStockInvariant(BigDecimal stockQty, BigDecimal lockedQty) {
        BigDecimal s = safeQty(stockQty);
        BigDecimal l = safeQty(lockedQty);
        if (l.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid stock state: locked_qty < 0");
        }
        if (l.compareTo(s) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid stock state: locked_qty > stock_qty");
        }
    }

    private static void validateLines(List<SalReturnLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }
        Set<Long> seen = new HashSet<>();
        for (SalReturnLineRequest line : lines) {
            if (line == null || line.productId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
            }
            if (!seen.add(line.productId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate productId: " + line.productId());
            }
            BigDecimal qty = safeQty(line.qty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be positive");
            }
            BigDecimal price = safeMoney(line.price());
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be >= 0");
            }
        }
    }

    private static SalReturnResponse toResponse(SalReturnRepository.SalReturnRow row) {
        if (row == null) return null;
        return new SalReturnResponse(
                row.getId(),
                row.getReturnNo(),
                row.getCustomerId(),
                row.getCustomerCode(),
                row.getCustomerName(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getReturnDate(),
                row.getTotalQty(),
                row.getTotalAmount(),
                row.getStatus(),
                row.getRemark(),
                row.getWmsBillId(),
                row.getWmsBillNo(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getAuditBy(),
                row.getAuditTime(),
                row.getExecuteBy(),
                row.getExecuteTime());
    }

    private static BigDecimal safeQty(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String appendRemark(String remark, String extra) {
        String r = trimToNull(remark);
        String e = trimToNull(extra);
        if (e == null) return r;
        if (r == null) return e;
        return r + "；" + e;
    }

    private static String generateReturnNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "SR" + ts + "-" + rand;
    }

    private static String generateWmsBillNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "SRI" + ts + "-" + rand;
    }
}
