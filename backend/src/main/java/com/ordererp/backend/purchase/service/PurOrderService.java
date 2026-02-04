package com.ordererp.backend.purchase.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.purchase.dto.PurOrderCreateRequest;
import com.ordererp.backend.purchase.dto.PurOrderDetailResponse;
import com.ordererp.backend.purchase.dto.PurOrderInboundRequest;
import com.ordererp.backend.purchase.dto.PurOrderInboundResponse;
import com.ordererp.backend.purchase.dto.PurOrderItemResponse;
import com.ordererp.backend.purchase.dto.PurOrderLineRequest;
import com.ordererp.backend.purchase.dto.PurOrderResponse;
import com.ordererp.backend.purchase.entity.PurOrder;
import com.ordererp.backend.purchase.entity.PurOrderDetail;
import com.ordererp.backend.purchase.repository.PurOrderDetailRepository;
import com.ordererp.backend.purchase.repository.PurOrderRepository;
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
public class PurOrderService {
    private static final int STATUS_PENDING_AUDIT = 1;
    private static final int STATUS_AUDITED = 2;
    private static final int STATUS_COMPLETED = 4;
    private static final int STATUS_CANCELED = 9;

    private static final int PARTNER_TYPE_SUPPLIER = 1;

    private static final int WMS_BILL_TYPE_PURCHASE_IN = 1;
    private static final int WMS_BILL_STATUS_COMPLETED = 2;

    private final PurOrderRepository orderRepository;
    private final PurOrderDetailRepository detailRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockLogRepository stockLogRepository;

    public PurOrderService(PurOrderRepository orderRepository, PurOrderDetailRepository detailRepository,
            BasePartnerRepository partnerRepository, BaseWarehouseRepository warehouseRepository,
            BaseProductRepository productRepository, WmsIoBillRepository ioBillRepository,
            WmsIoBillDetailRepository ioBillDetailRepository, WmsStockRepository stockRepository,
            WmsStockLogRepository stockLogRepository) {
        this.orderRepository = orderRepository;
        this.detailRepository = detailRepository;
        this.partnerRepository = partnerRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockRepository = stockRepository;
        this.stockLogRepository = stockLogRepository;
    }

    public Page<PurOrderResponse> page(String keyword, Pageable pageable) {
        return orderRepository.pageRows(trimToNull(keyword), pageable).map(PurOrderService::toResponse);
    }

    public PurOrderDetailResponse detail(Long id) {
        PurOrderRepository.PurOrderRow row = orderRepository.getRow(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在");
        }
        List<PurOrderItemResponse> items = detailRepository.findByOrderIdOrderByIdAsc(id).stream()
                .map(PurOrderService::toItemResponse)
                .toList();
        return new PurOrderDetailResponse(
                row.getId(),
                row.getOrderNo(),
                row.getSupplierId(),
                row.getSupplierCode(),
                row.getSupplierName(),
                row.getOrderDate(),
                row.getTotalAmount(),
                row.getPayAmount(),
                row.getStatus(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getAuditBy(),
                row.getAuditTime(),
                items);
    }

    @Transactional
    public PurOrderResponse create(PurOrderCreateRequest request, String createdBy) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        if (request.supplierId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "supplierId is required");
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }
        validateLines(request.lines());

        BasePartner supplier = partnerRepository.findByIdAndDeleted(request.supplierId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商不存在"));
        if (supplier.getStatus() != null && supplier.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商已禁用");
        }
        if (!Objects.equals(supplier.getType(), PARTNER_TYPE_SUPPLIER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partner 不是供应商(type=1)");
        }

        LocalDate orderDate = request.orderDate() != null ? request.orderDate() : LocalDate.now();

        PurOrder order = new PurOrder();
        order.setOrderNo(generateOrderNo());
        order.setSupplierId(request.supplierId());
        order.setOrderDate(orderDate);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPayAmount(BigDecimal.ZERO);
        order.setStatus(STATUS_PENDING_AUDIT);
        order.setRemark(trimToNull(request.remark()));
        order.setCreateBy(trimToNull(createdBy));
        order.setCreateTime(LocalDateTime.now());

        order = orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (PurOrderLineRequest line : request.lines()) {
            BaseProduct product = productRepository.findByIdAndDeleted(line.productId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在: " + line.productId()));
            if (product.getStatus() != null && product.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已禁用: " + product.getProductCode());
            }

            BigDecimal qty = safeQty(line.qty());
            BigDecimal price = safeMoney(line.price());
            BigDecimal amount = price.multiply(qty);

            PurOrderDetail d = new PurOrderDetail();
            d.setOrderId(order.getId());
            d.setProductId(product.getId());
            d.setProductCode(product.getProductCode());
            d.setProductName(product.getProductName());
            d.setUnit(product.getUnit());
            d.setPrice(price);
            d.setQty(qty);
            d.setAmount(amount);
            d.setInQty(BigDecimal.ZERO);
            detailRepository.save(d);

            total = total.add(amount);
        }

        order.setTotalAmount(total);
        PurOrder saved = orderRepository.save(order);
        return toResponse(orderRepository.getRow(saved.getId()));
    }

    @Transactional
    public PurOrderResponse audit(Long id, String auditBy) {
        PurOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在"));
        if (Objects.equals(order.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已作废");
        }
        if (!Objects.equals(order.getStatus(), STATUS_PENDING_AUDIT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单状态不允许审核");
        }

        order.setStatus(STATUS_AUDITED);
        order.setAuditBy(trimToNull(auditBy));
        order.setAuditTime(LocalDateTime.now());
        orderRepository.save(order);
        return toResponse(orderRepository.getRow(order.getId()));
    }

    /**
     * 采购入库：一次性把采购单全部入库并完成采购单。
     *
     * <p>说明：</p>
     * <ul>
     *   <li>本阶段实现的是“进货 -> 入库 -> 加库存”的最小闭环。</li>
     *   <li>入库接口幂等：已完成的采购单重复入库，会返回已生成的入库单，不会重复加库存。</li>
     * </ul>
     */
    @Transactional
    public PurOrderInboundResponse inbound(Long id, PurOrderInboundRequest request, String operator) {
        if (request == null || request.warehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId is required");
        }

        PurOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在"));
        if (Objects.equals(order.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已作废");
        }

        // 采购域不使用 wms_io_bill.biz_id（该字段在 WMS 内部用于冲销等语义，且存在全表 unique(biz_id,type) 约束）。
        // 这里用 biz_no=采购单号 + type 作为幂等键。
        WmsIoBill existingBill = ioBillRepository.findFirstByBizNoAndType(order.getOrderNo(), WMS_BILL_TYPE_PURCHASE_IN).orElse(null);
        if (existingBill != null) {
            if (!Objects.equals(order.getStatus(), STATUS_COMPLETED)) {
                order.setStatus(STATUS_COMPLETED);
                orderRepository.save(order);
            }
            return new PurOrderInboundResponse(order.getId(), order.getOrderNo(), order.getStatus(),
                    existingBill.getId(), existingBill.getBillNo());
        }

        if (!Objects.equals(order.getStatus(), STATUS_AUDITED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单未审核，不能入库");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(request.warehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        List<PurOrderDetail> details = detailRepository.findByOrderIdOrderByIdAsc(order.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单没有明细");
        }

        WmsIoBill bill = new WmsIoBill();
        bill.setBillNo(generateBillNo("PI"));
        bill.setType(WMS_BILL_TYPE_PURCHASE_IN);
        bill.setBizId(null);
        bill.setBizNo(order.getOrderNo());
        bill.setWarehouseId(wh.getId());
        bill.setStatus(WMS_BILL_STATUS_COMPLETED);
        bill.setRemark("采购入库: " + order.getOrderNo());
        bill.setCreateBy(trimToNull(operator));
        bill.setCreateTime(LocalDateTime.now());

        try {
            bill = ioBillRepository.saveAndFlush(bill);
        } catch (DataIntegrityViolationException e) {
            existingBill = ioBillRepository.findFirstByBizNoAndType(order.getOrderNo(), WMS_BILL_TYPE_PURCHASE_IN)
                    .orElseThrow(() -> e);
            return new PurOrderInboundResponse(order.getId(), order.getOrderNo(), order.getStatus(),
                    existingBill.getId(), existingBill.getBillNo());
        }

        for (PurOrderDetail d : details) {
            BigDecimal qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 入库：增加库存
            WmsStock stock = increaseStockWithRetry(wh.getId(), d.getProductId(), qty);

            // 记账：已入库数量 = 采购数量（本阶段为一次性全入库）
            d.setInQty(qty);
            detailRepository.save(d);

            WmsIoBillDetail bd = new WmsIoBillDetail();
            bd.setBillId(bill.getId());
            bd.setProductId(d.getProductId());
            bd.setQty(qty);
            bd.setRealQty(qty);
            ioBillDetailRepository.save(bd);

            WmsStockLog log = new WmsStockLog();
            log.setWarehouseId(wh.getId());
            log.setProductId(d.getProductId());
            log.setBizType("PURCHASE_IN");
            log.setBizNo(bill.getBillNo());
            log.setChangeQty(qty);
            log.setAfterStockQty(stock.getStockQty());
            log.setCreateTime(LocalDateTime.now());
            stockLogRepository.save(log);
        }

        order.setStatus(STATUS_COMPLETED);
        orderRepository.save(order);

        return new PurOrderInboundResponse(order.getId(), order.getOrderNo(), order.getStatus(), bill.getId(), bill.getBillNo());
    }

    @Transactional
    public PurOrderResponse cancel(Long id, String operator) {
        PurOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在"));
        if (Objects.equals(order.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已完成，不能作废");
        }
        if (Objects.equals(order.getStatus(), STATUS_CANCELED)) {
            return toResponse(orderRepository.getRow(order.getId()));
        }

        order.setStatus(STATUS_CANCELED);
        order.setRemark(appendRemark(order.getRemark(), "作废: " + trimToNull(operator)));
        orderRepository.save(order);
        return toResponse(orderRepository.getRow(order.getId()));
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

    private static PurOrderResponse toResponse(PurOrderRepository.PurOrderRow row) {
        if (row == null) return null;
        return new PurOrderResponse(
                row.getId(),
                row.getOrderNo(),
                row.getSupplierId(),
                row.getSupplierCode(),
                row.getSupplierName(),
                row.getOrderDate(),
                row.getTotalAmount(),
                row.getPayAmount(),
                row.getStatus(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getAuditBy(),
                row.getAuditTime());
    }

    private static PurOrderItemResponse toItemResponse(PurOrderDetail d) {
        return new PurOrderItemResponse(
                d.getId(),
                d.getProductId(),
                d.getProductCode(),
                d.getProductName(),
                d.getUnit(),
                d.getPrice(),
                d.getQty(),
                d.getAmount(),
                d.getInQty());
    }

    private static void validateLines(List<PurOrderLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (PurOrderLineRequest line : lines) {
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

    private static String generateOrderNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "PO" + date + "-" + rand;
    }

    private static String generateBillNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return prefix + ts + "-" + rand;
    }
}
