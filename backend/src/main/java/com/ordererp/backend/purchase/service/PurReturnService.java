package com.ordererp.backend.purchase.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.purchase.dto.PurReturnCreateRequest;
import com.ordererp.backend.purchase.dto.PurReturnDetailResponse;
import com.ordererp.backend.purchase.dto.PurReturnExecuteResponse;
import com.ordererp.backend.purchase.dto.PurReturnItemResponse;
import com.ordererp.backend.purchase.dto.PurReturnLineRequest;
import com.ordererp.backend.purchase.dto.PurReturnResponse;
import com.ordererp.backend.purchase.entity.PurReturn;
import com.ordererp.backend.purchase.entity.PurReturnDetail;
import com.ordererp.backend.purchase.repository.PurReturnDetailRepository;
import com.ordererp.backend.purchase.repository.PurReturnRepository;
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
public class PurReturnService {
    private static final int STATUS_PENDING_AUDIT = 1;
    private static final int STATUS_AUDITED = 2;
    private static final int STATUS_COMPLETED = 4;
    private static final int STATUS_CANCELED = 9;

    private static final int PARTNER_TYPE_SUPPLIER = 1;

    private static final int WMS_BILL_TYPE_PURCHASE_RETURN = 2;
    private static final int WMS_BILL_STATUS_COMPLETED = 2;

    private final PurReturnRepository returnRepository;
    private final PurReturnDetailRepository detailRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockLogRepository stockLogRepository;

    public PurReturnService(PurReturnRepository returnRepository, PurReturnDetailRepository detailRepository,
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

    public Page<PurReturnResponse> page(String keyword, Pageable pageable) {
        return returnRepository.pageRows(trimToNull(keyword), pageable).map(PurReturnService::toResponse);
    }

    public PurReturnDetailResponse detail(Long id) {
        PurReturnRepository.PurReturnRow row = returnRepository.getRow(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在");
        }
        List<PurReturnItemResponse> items = detailRepository.findByReturnIdOrderByIdAsc(id).stream()
                .map(PurReturnService::toItemResponse)
                .toList();
        return new PurReturnDetailResponse(toResponse(row), items);
    }

    @Transactional
    public PurReturnResponse create(PurReturnCreateRequest request, String operator) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        if (request.supplierId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "supplierId is required");
        }
        if (request.warehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId is required");
        }
        validateLines(request.lines());

        BasePartner supplier = partnerRepository.findByIdAndDeleted(request.supplierId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商不存在"));
        if (supplier.getType() != null && supplier.getType() != PARTNER_TYPE_SUPPLIER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来单位不是供应商");
        }
        if (supplier.getStatus() != null && supplier.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商已禁用");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(request.warehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        PurReturn header = new PurReturn();
        header.setReturnNo(generateReturnNo());
        header.setSupplierId(supplier.getId());
        header.setWarehouseId(wh.getId());
        header.setReturnDate(request.returnDate() == null ? LocalDate.now() : request.returnDate());
        header.setTotalAmount(BigDecimal.ZERO);
        header.setStatus(STATUS_PENDING_AUDIT);
        header.setRemark(trimToNull(request.remark()));
        header.setCreateBy(trimToNull(operator));
        header.setCreateTime(LocalDateTime.now());

        header = returnRepository.saveAndFlush(header);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurReturnLineRequest line : request.lines()) {
            BaseProduct product = productRepository.findByIdAndDeleted(line.productId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在: " + line.productId()));
            if (product.getStatus() != null && product.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已禁用: " + product.getProductCode());
            }

            BigDecimal qty = safeQty(line.qty());
            BigDecimal price = safeMoney(line.price());
            BigDecimal amount = price.multiply(qty);

            PurReturnDetail d = new PurReturnDetail();
            d.setReturnId(header.getId());
            d.setProductId(product.getId());
            d.setProductCode(product.getProductCode());
            d.setProductName(product.getProductName());
            d.setUnit(product.getUnit());
            d.setPrice(price);
            d.setQty(qty);
            d.setAmount(amount);
            detailRepository.save(d);

            totalAmount = totalAmount.add(amount);
        }

        header.setTotalAmount(totalAmount);
        returnRepository.save(header);
        return toResponse(returnRepository.getRow(header.getId()));
    }

    @Transactional
    public PurReturnResponse audit(Long id, String auditBy) {
        PurReturn r = returnRepository.findByIdForUpdate(id)
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
     * 执行退货：生成 WMS 出库单（type=2）并扣减库存。
     *
     * <p>幂等语义：</p>
     * <ul>
     *   <li>同一退货单重复 execute 不会重复扣库存：已完成则直接返回关联 WMS 单。</li>
     *   <li>实现方式：对退货单主表加悲观锁 + 以 returnNo 追溯关联 WMS 单（biz_no）。</li>
     * </ul>
     */
    @Transactional
    public PurReturnExecuteResponse execute(Long id, String operator) {
        PurReturn r = returnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在"));
        if (Objects.equals(r.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已作废");
        }

        WmsIoBill existing = ioBillRepository.findFirstByBizNoAndType(r.getReturnNo(), WMS_BILL_TYPE_PURCHASE_RETURN).orElse(null);
        if (existing != null) {
            if (!Objects.equals(r.getStatus(), STATUS_COMPLETED)) {
                r.setStatus(STATUS_COMPLETED);
                r.setWmsBillId(existing.getId());
                r.setWmsBillNo(existing.getBillNo());
                r.setExecuteBy(trimToNull(operator));
                r.setExecuteTime(LocalDateTime.now());
                returnRepository.save(r);
            }
            return new PurReturnExecuteResponse(r.getId(), r.getReturnNo(), r.getStatus(), existing.getId(), existing.getBillNo());
        }

        if (!Objects.equals(r.getStatus(), STATUS_AUDITED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单未审核，不能执行");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(r.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        List<PurReturnDetail> details = detailRepository.findByReturnIdOrderByIdAsc(r.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单没有明细");
        }

        // 预检查：库存可用量必须充足
        for (PurReturnDetail d : details) {
            BigDecimal qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid qty on detail: " + d.getId());
            }
            WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), d.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock not found: productId=" + d.getProductId()));
            BigDecimal stockQty = safeQty(stock.getStockQty());
            BigDecimal lockedQty = safeQty(stock.getLockedQty());
            validateStockInvariant(stockQty, lockedQty);
            BigDecimal available = stockQty.subtract(lockedQty);
            if (available.compareTo(qty) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "insufficient stock (productId=" + d.getProductId() + ", available=" + available + ", required=" + qty + ")");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        WmsIoBill bill = new WmsIoBill();
        bill.setBillNo(generateBillNo("PR"));
        bill.setType(WMS_BILL_TYPE_PURCHASE_RETURN);
        bill.setBizId(null);
        bill.setBizNo(r.getReturnNo());
        bill.setWarehouseId(wh.getId());
        bill.setStatus(WMS_BILL_STATUS_COMPLETED);
        bill.setRemark("采购退货: " + r.getReturnNo());
        bill.setCreateBy(trimToNull(operator));
        bill.setCreateTime(now);
        try {
            bill = ioBillRepository.saveAndFlush(bill);
        } catch (DataIntegrityViolationException e) {
            existing = ioBillRepository.findFirstByBizNoAndType(r.getReturnNo(), WMS_BILL_TYPE_PURCHASE_RETURN).orElse(null);
            if (existing != null) {
                return new PurReturnExecuteResponse(r.getId(), r.getReturnNo(), r.getStatus(), existing.getId(), existing.getBillNo());
            }
            throw e;
        }

        for (PurReturnDetail d : details) {
            BigDecimal qty = safeQty(d.getQty());

            WmsIoBillDetail bd = new WmsIoBillDetail();
            bd.setBillId(bill.getId());
            bd.setProductId(d.getProductId());
            bd.setQty(qty);
            bd.setRealQty(qty);
            ioBillDetailRepository.save(bd);

            WmsStock stock = deductStockWithRetry(wh.getId(), d.getProductId(), qty);
            WmsStockLog log = new WmsStockLog();
            log.setWarehouseId(wh.getId());
            log.setProductId(d.getProductId());
            log.setBizType("PURCHASE_RETURN");
            log.setBizNo(bill.getBillNo());
            log.setChangeQty(qty.negate());
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

        return new PurReturnExecuteResponse(r.getId(), r.getReturnNo(), r.getStatus(), bill.getId(), bill.getBillNo());
    }

    @Transactional
    public PurReturnResponse cancel(Long id, String operator) {
        PurReturn r = returnRepository.findByIdForUpdate(id)
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

    private WmsStock deductStockWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return deductStockOnce(warehouseId, productId, qty);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private WmsStock deductStockOnce(Long warehouseId, Long productId, BigDecimal qty) {
        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock not found"));

        BigDecimal stockQty = safeQty(stock.getStockQty());
        BigDecimal lockedQty = safeQty(stock.getLockedQty());
        validateStockInvariant(stockQty, lockedQty);
        BigDecimal available = stockQty.subtract(lockedQty);
        if (available.compareTo(qty) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "insufficient stock (available=" + available + ", required=" + qty + ")");
        }
        stock.setStockQty(stockQty.subtract(qty));
        stock.setLockedQty(lockedQty);
        stock.setUpdateTime(LocalDateTime.now());
        validateStockInvariant(stock.getStockQty(), stock.getLockedQty());
        return stockRepository.saveAndFlush(stock);
    }

    private static PurReturnResponse toResponse(PurReturnRepository.PurReturnRow row) {
        if (row == null) return null;
        return new PurReturnResponse(
                row.getId(),
                row.getReturnNo(),
                row.getSupplierId(),
                row.getSupplierCode(),
                row.getSupplierName(),
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

    private static PurReturnItemResponse toItemResponse(PurReturnDetail d) {
        return new PurReturnItemResponse(
                d.getId(),
                d.getProductId(),
                d.getProductCode(),
                d.getProductName(),
                d.getUnit(),
                d.getPrice(),
                d.getQty(),
                d.getAmount());
    }

    private static void validateLines(List<PurReturnLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }
        Set<Long> seen = new HashSet<>();
        for (PurReturnLineRequest line : lines) {
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

    private static BigDecimal safeQty(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void validateStockInvariant(BigDecimal stockQty, BigDecimal lockedQty) {
        BigDecimal s = safeQty(stockQty);
        BigDecimal l = safeQty(lockedQty);
        if (s.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid stock state: stock_qty < 0");
        }
        if (l.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid stock state: locked_qty < 0");
        }
        if (l.compareTo(s) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid stock state: locked_qty > stock_qty");
        }
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
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "PRN" + date + "-" + rand;
    }

    private static String generateBillNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return prefix + ts + "-" + rand;
    }
}

