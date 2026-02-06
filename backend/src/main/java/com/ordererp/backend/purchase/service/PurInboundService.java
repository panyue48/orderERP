package com.ordererp.backend.purchase.service;

import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.purchase.dto.PurInboundCreateLineRequest;
import com.ordererp.backend.purchase.dto.PurInboundCreateRequest;
import com.ordererp.backend.purchase.dto.PurInboundDetailResponse;
import com.ordererp.backend.purchase.dto.PurInboundExecuteResponse;
import com.ordererp.backend.purchase.dto.PurInboundItemResponse;
import com.ordererp.backend.purchase.dto.PurInboundNewOrderLineRequest;
import com.ordererp.backend.purchase.dto.PurInboundNewOrderRequest;
import com.ordererp.backend.purchase.dto.PurInboundReverseResponse;
import com.ordererp.backend.purchase.dto.PurInboundResponse;
import com.ordererp.backend.purchase.entity.PurInbound;
import com.ordererp.backend.purchase.entity.PurInboundDetail;
import com.ordererp.backend.purchase.entity.PurOrder;
import com.ordererp.backend.purchase.entity.PurOrderDetail;
import com.ordererp.backend.purchase.repository.PurApDocRefRepository;
import com.ordererp.backend.purchase.repository.PurInboundDetailRepository;
import com.ordererp.backend.purchase.repository.PurInboundRepository;
import com.ordererp.backend.purchase.repository.PurOrderDetailRepository;
import com.ordererp.backend.purchase.repository.PurOrderRepository;
import com.ordererp.backend.wms.entity.WmsIoBill;
import com.ordererp.backend.wms.entity.WmsIoBillDetail;
import com.ordererp.backend.wms.entity.WmsStock;
import com.ordererp.backend.wms.entity.WmsStockQc;
import com.ordererp.backend.wms.entity.WmsStockLog;
import com.ordererp.backend.wms.repository.WmsIoBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsIoBillRepository;
import com.ordererp.backend.wms.repository.WmsStockQcRepository;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import com.ordererp.backend.wms.repository.WmsStockRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
public class PurInboundService {
    private static final int ORDER_STATUS_AUDITED = 2;
    private static final int ORDER_STATUS_PARTIAL = 3;
    private static final int ORDER_STATUS_COMPLETED = 4;
    private static final int ORDER_STATUS_CANCELED = 9;

    private static final int INBOUND_STATUS_PENDING_QC = 1;
    private static final int INBOUND_STATUS_COMPLETED = 2;
    private static final int INBOUND_STATUS_REVERSED = 8;
    private static final int INBOUND_STATUS_CANCELED = 9;

    private static final int QC_STATUS_PENDING = 1;
    private static final int QC_STATUS_PASSED = 2;
    private static final int QC_STATUS_REJECTED = 3;

    private static final int WMS_BILL_TYPE_PURCHASE_IN = 1;
    private static final int WMS_BILL_TYPE_STOCK_OUT = 4;
    private static final int WMS_BILL_STATUS_COMPLETED = 2;

    private static final int DOC_TYPE_INBOUND = 1;
    private static final int REVERSE_STATUS_REVERSED = 1;

    private final PurInboundRepository inboundRepository;
    private final PurInboundDetailRepository inboundDetailRepository;
    private final PurOrderRepository orderRepository;
    private final PurOrderDetailRepository orderDetailRepository;
    private final PurApDocRefRepository docRefRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockQcRepository stockQcRepository;
    private final WmsStockLogRepository stockLogRepository;

    public PurInboundService(PurInboundRepository inboundRepository, PurInboundDetailRepository inboundDetailRepository,
            PurOrderRepository orderRepository, PurOrderDetailRepository orderDetailRepository,
            PurApDocRefRepository docRefRepository,
            BasePartnerRepository partnerRepository, BaseWarehouseRepository warehouseRepository, BaseProductRepository productRepository,
            WmsIoBillRepository ioBillRepository, WmsIoBillDetailRepository ioBillDetailRepository,
            WmsStockRepository stockRepository, WmsStockQcRepository stockQcRepository, WmsStockLogRepository stockLogRepository) {
        this.inboundRepository = inboundRepository;
        this.inboundDetailRepository = inboundDetailRepository;
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.docRefRepository = docRefRepository;
        this.partnerRepository = partnerRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockRepository = stockRepository;
        this.stockQcRepository = stockQcRepository;
        this.stockLogRepository = stockLogRepository;
    }

    public Page<PurInboundResponse> page(String keyword, Long orderId, Pageable pageable) {
        return inboundRepository.pageRows(trimToNull(keyword), orderId, pageable).map(PurInboundService::toResponse);
    }

    public com.ordererp.backend.purchase.dto.PurPendingQcSummaryResponse pendingQcSummary(Long orderId) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }
        PurInboundRepository.PendingQcSummaryRow row = inboundRepository.pendingQcSummary(orderId);
        long pendingCount = row == null || row.getPendingCount() == null ? 0L : row.getPendingCount();
        BigDecimal pendingQty = row == null || row.getPendingQty() == null ? BigDecimal.ZERO : row.getPendingQty();

        List<com.ordererp.backend.purchase.dto.PurPendingQcItemResponse> items = inboundRepository.pendingQcSummaryItems(orderId).stream()
                .map(r -> new com.ordererp.backend.purchase.dto.PurPendingQcItemResponse(
                        r.getProductId(),
                        r.getProductCode(),
                        r.getProductName(),
                        r.getUnit(),
                        r.getQty()))
                .toList();

        return new com.ordererp.backend.purchase.dto.PurPendingQcSummaryResponse(orderId, pendingCount, pendingQty, items);
    }

    public PurInboundDetailResponse detail(Long id) {
        PurInboundRepository.PurInboundRow row = inboundRepository.getRow(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "入库单不存在");
        }
        List<PurInboundItemResponse> items = inboundDetailRepository.findByInboundIdOrderByIdAsc(id).stream()
                .map(d -> new PurInboundItemResponse(
                        d.getId(),
                        d.getProductId(),
                        d.getProductCode(),
                        d.getProductName(),
                        d.getUnit(),
                        d.getPlanQty(),
                        d.getRealQty()))
                .toList();
        return new PurInboundDetailResponse(toResponse(row), items);
    }

    /**
     * 在“采购入库单”页面发起：创建采购订单（pur_order）并生成/执行一张入库单（pur_inbound）。
     *
     * <p>幂等语义：</p>
     * <ul>
     *   <li>由客户端提供 requestNo（UUID）。重复提交相同 requestNo 必须返回同一张入库单，不重复加库存。</li>
     *   <li>实现方式：pur_inbound.request_no 唯一约束 + 并发下捕获唯一键冲突回查 existing。</li>
     * </ul>
     */
    @Transactional
    public PurInboundExecuteResponse createAndExecuteNewOrder(PurInboundNewOrderRequest request, String operator) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        String requestNo = trimToNull(request.requestNo());
        if (requestNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestNo is required");
        }
        if (request.supplierId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "supplierId is required");
        }
        if (request.warehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId is required");
        }
        validateNewOrderLines(request.lines());

        // requestNo 全局幂等（pur_inbound）：重复提交直接返回 existing，不再创建新采购单。
        PurInbound existing = inboundRepository.findFirstByRequestNo(requestNo).orElse(null);
        if (existing != null) {
            PurOrderRepository.PurOrderRow orderRow = orderRepository.getRow(existing.getOrderId());
            return new PurInboundExecuteResponse(
                    existing.getId(),
                    existing.getInboundNo(),
                    existing.getOrderId(),
                    existing.getOrderNo(),
                    orderRow == null ? null : orderRow.getStatus(),
                    existing.getWmsBillId(),
                    existing.getWmsBillNo());
        }

        BasePartner supplier = partnerRepository.findByIdAndDeleted(request.supplierId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商不存在"));
        if (supplier.getType() != null && supplier.getType() != 1) {
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

        LocalDateTime now = LocalDateTime.now();
        PurOrder order = new PurOrder();
        order.setOrderNo(generateOrderNo());
        order.setSupplierId(supplier.getId());
        order.setOrderDate(request.orderDate() == null ? LocalDate.now() : request.orderDate());
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPayAmount(BigDecimal.ZERO);
        order.setStatus(ORDER_STATUS_AUDITED);
        order.setRemark(trimToNull(request.remark()));
        order.setCreateBy(trimToNull(operator));
        order.setCreateTime(now);
        order.setAuditBy(trimToNull(operator));
        order.setAuditTime(now);
        order = orderRepository.saveAndFlush(order);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurInboundNewOrderLineRequest line : request.lines()) {
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
            orderDetailRepository.save(d);

            totalAmount = totalAmount.add(amount);
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        List<PurInboundCreateLineRequest> inboundLines = request.lines().stream()
                .map(l -> new PurInboundCreateLineRequest(l.productId(), safeQty(l.inboundQty())))
                .filter(l -> safeQty(l.qty()).compareTo(BigDecimal.ZERO) > 0)
                .toList();

        PurInboundCreateRequest inboundRequest = new PurInboundCreateRequest(
                requestNo,
                request.warehouseId(),
                request.remark(),
                inboundLines);
        return createFromOrder(order.getId(), inboundRequest, operator);
    }

    /**
     * 采购分批入库（从采购订单发起）：创建一张入库单并立刻执行。
     *
     * <p>幂等语义：</p>
     * <ul>
     *   <li>由客户端提供 requestNo（UUID）。重复提交相同 requestNo 必须返回同一张入库单，不重复加库存。</li>
     *   <li>实现方式：pur_inbound.request_no 唯一约束 + 并发下捕获唯一键冲突回查 existing。</li>
     * </ul>
     */
    @Transactional
    public PurInboundExecuteResponse createFromOrder(Long orderId, PurInboundCreateRequest request, String operator) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        String requestNo = trimToNull(request.requestNo());
        if (requestNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestNo is required");
        }
        if (request.warehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId is required");
        }
        validateLines(request.lines());

        PurInbound existing = inboundRepository.findFirstByRequestNo(requestNo).orElse(null);
        if (existing != null) {
            PurOrderRepository.PurOrderRow orderRow = orderRepository.getRow(existing.getOrderId());
            return new PurInboundExecuteResponse(
                    existing.getId(),
                    existing.getInboundNo(),
                    existing.getOrderId(),
                    existing.getOrderNo(),
                    orderRow == null ? null : orderRow.getStatus(),
                    existing.getWmsBillId(),
                    existing.getWmsBillNo());
        }

        PurOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在"));
        if (Objects.equals(order.getStatus(), ORDER_STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已作废");
        }
        if (Objects.equals(order.getStatus(), ORDER_STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已完成");
        }
        if (!Objects.equals(order.getStatus(), ORDER_STATUS_AUDITED) && !Objects.equals(order.getStatus(), ORDER_STATUS_PARTIAL)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单未审核，不能入库");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(request.warehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        List<PurOrderDetail> orderDetails = orderDetailRepository.findByOrderIdOrderByIdAsc(order.getId());
        if (orderDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单没有明细");
        }
        Map<Long, PurOrderDetail> orderDetailByProductId = new HashMap<>();
        for (PurOrderDetail d : orderDetails) {
            orderDetailByProductId.put(d.getProductId(), d);
        }

        // 校验并计算：入库数量不能超过剩余数量。
        for (PurInboundCreateLineRequest line : request.lines()) {
            PurOrderDetail od = orderDetailByProductId.get(line.productId());
            if (od == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不在采购单内: productId=" + line.productId());
            }
            BigDecimal qty = safeQty(line.qty());
            BigDecimal ordered = safeQty(od.getQty());
            BigDecimal inQty = safeQty(od.getInQty());
            BigDecimal remaining = ordered.subtract(inQty);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已全部入库: productId=" + line.productId());
            }
            if (qty.compareTo(remaining) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "入库数量超过剩余数量: productId=" + line.productId() + ", remaining=" + remaining + ", inbound=" + qty);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        PurInbound inbound = new PurInbound();
        inbound.setInboundNo(generateInboundNo());
        inbound.setRequestNo(requestNo);
        inbound.setOrderId(order.getId());
        inbound.setOrderNo(order.getOrderNo());
        inbound.setSupplierId(order.getSupplierId());
        inbound.setWarehouseId(wh.getId());
        inbound.setStatus(INBOUND_STATUS_PENDING_QC);
        inbound.setQcStatus(QC_STATUS_PENDING);
        inbound.setRemark(trimToNull(request.remark()));
        inbound.setCreateBy(trimToNull(operator));
        inbound.setCreateTime(now);

        try {
            inbound = inboundRepository.saveAndFlush(inbound);
        } catch (DataIntegrityViolationException e) {
            existing = inboundRepository.findFirstByRequestNo(requestNo).orElse(null);
            if (existing != null) {
                PurOrderRepository.PurOrderRow orderRow = orderRepository.getRow(existing.getOrderId());
                return new PurInboundExecuteResponse(
                        existing.getId(),
                        existing.getInboundNo(),
                        existing.getOrderId(),
                        existing.getOrderNo(),
                        orderRow == null ? null : orderRow.getStatus(),
                        existing.getWmsBillId(),
                        existing.getWmsBillNo());
            }
            throw e;
        }

        // 保存明细（快照字段优先取采购单明细，保证历史一致）
        for (PurInboundCreateLineRequest line : request.lines()) {
            PurOrderDetail od = orderDetailByProductId.get(line.productId());
            BigDecimal qty = safeQty(line.qty());

            // 商品有效性校验（避免采购单审核后商品被禁用导致入库异常）
            BaseProduct product = productRepository.findByIdAndDeleted(line.productId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在: " + line.productId()));
            if (product.getStatus() != null && product.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已禁用: " + product.getProductCode());
            }

            PurInboundDetail d = new PurInboundDetail();
            d.setInboundId(inbound.getId());
            d.setProductId(line.productId());
            d.setProductCode(od.getProductCode());
            d.setProductName(od.getProductName());
            d.setUnit(od.getUnit());
            d.setPlanQty(qty);
            d.setRealQty(BigDecimal.ZERO);
            inboundDetailRepository.save(d);
        }

        // 到货先进入“待检库存”，不影响可用库存
        Map<Long, BigDecimal> qcDeltaByProductId = new HashMap<>();
        for (PurInboundCreateLineRequest line : request.lines()) {
            qcDeltaByProductId.merge(line.productId(), safeQty(line.qty()), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : qcDeltaByProductId.entrySet()) {
            BigDecimal qty = safeQty(e.getValue());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            increaseQcWithRetry(wh.getId(), e.getKey(), qty);
        }

        return new PurInboundExecuteResponse(
                inbound.getId(),
                inbound.getInboundNo(),
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                inbound.getWmsBillId(),
                inbound.getWmsBillNo());
    }

    @Transactional
    public PurInboundExecuteResponse iqcPassAndExecute(Long inboundId, String qcRemark, String operator) {
        PurInbound inbound = inboundRepository.findByIdForUpdate(inboundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "入库单不存在"));
        if (Objects.equals(inbound.getStatus(), INBOUND_STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库单已作废");
        }

        if (inbound.getWmsBillId() != null || inbound.getWmsBillNo() != null) {
            // 已执行：幂等返回
            PurOrderRepository.PurOrderRow orderRow = orderRepository.getRow(inbound.getOrderId());
            return new PurInboundExecuteResponse(
                    inbound.getId(),
                    inbound.getInboundNo(),
                    inbound.getOrderId(),
                    inbound.getOrderNo(),
                    orderRow == null ? null : orderRow.getStatus(),
                    inbound.getWmsBillId(),
                    inbound.getWmsBillNo());
        }

        inbound.setQcStatus(QC_STATUS_PASSED);
        inbound.setQcBy(trimToNull(operator));
        inbound.setQcTime(LocalDateTime.now());
        inbound.setQcRemark(trimToNull(qcRemark));
        inboundRepository.save(inbound);

        return executeInbound(inbound, operator);
    }

    @Transactional
    public PurInboundResponse iqcReject(Long inboundId, String qcRemark, String operator) {
        PurInbound inbound = inboundRepository.findByIdForUpdate(inboundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "入库单不存在"));
        if (Objects.equals(inbound.getStatus(), INBOUND_STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库单已完成，不能判定不合格");
        }
        if (Objects.equals(inbound.getStatus(), INBOUND_STATUS_CANCELED)) {
            return toResponse(inboundRepository.getRow(inbound.getId()));
        }

        // 质检不合格：从“待检库存”扣回（不进入可用库存）
        List<PurInboundDetail> inboundDetails = inboundDetailRepository.findByInboundIdOrderByIdAsc(inbound.getId());
        Map<Long, BigDecimal> qcDeltaByProductId = new HashMap<>();
        for (PurInboundDetail d : inboundDetails) {
            BigDecimal qty = safeQty(d.getPlanQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            qcDeltaByProductId.merge(d.getProductId(), qty, BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : qcDeltaByProductId.entrySet()) {
            BigDecimal qty = safeQty(e.getValue());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            decreaseQcWithRetry(inbound.getWarehouseId(), e.getKey(), qty);
        }

        inbound.setQcStatus(QC_STATUS_REJECTED);
        inbound.setQcBy(trimToNull(operator));
        inbound.setQcTime(LocalDateTime.now());
        inbound.setQcRemark(trimToNull(qcRemark));
        inbound.setStatus(INBOUND_STATUS_CANCELED);
        inboundRepository.save(inbound);
        return toResponse(inboundRepository.getRow(inbound.getId()));
    }

    private PurInboundExecuteResponse executeInbound(PurInbound inbound, String operator) {
        if (inbound.getWarehouseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId missing");
        }
        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(inbound.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        PurOrder order = orderRepository.findByIdForUpdate(inbound.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在"));
        if (Objects.equals(order.getStatus(), ORDER_STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已作废");
        }
        if (Objects.equals(order.getStatus(), ORDER_STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已完成");
        }

        List<PurOrderDetail> orderDetails = orderDetailRepository.findByOrderIdOrderByIdAsc(order.getId());
        if (orderDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单没有明细");
        }
        Map<Long, PurOrderDetail> orderDetailByProductId = new HashMap<>();
        for (PurOrderDetail d : orderDetails) {
            orderDetailByProductId.put(d.getProductId(), d);
        }

        List<PurInboundDetail> inboundDetails = inboundDetailRepository.findByInboundIdOrderByIdAsc(inbound.getId());
        if (inboundDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库单没有明细");
        }

        // 执行入库：写 WMS 单据 + 加库存 + 写流水 + 回写采购单进度
        LocalDateTime now = LocalDateTime.now();
        WmsIoBill bill = new WmsIoBill();
        bill.setBillNo(generateBillNo("PI"));
        bill.setType(WMS_BILL_TYPE_PURCHASE_IN);
        bill.setBizId(null);
        bill.setBizNo(inbound.getInboundNo());
        bill.setWarehouseId(wh.getId());
        bill.setStatus(WMS_BILL_STATUS_COMPLETED);
        bill.setRemark("采购入库: " + order.getOrderNo() + " / " + inbound.getInboundNo());
        bill.setCreateBy(trimToNull(operator));
        bill.setCreateTime(now);
        bill = ioBillRepository.saveAndFlush(bill);

        for (PurInboundDetail d : inboundDetails) {
            BigDecimal qty = safeQty(d.getPlanQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 待检库存 -> 可用库存（质检通过后才增加物理库存）
            decreaseQcWithRetry(wh.getId(), d.getProductId(), qty);

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
            log.setBizType("PURCHASE_IN");
            log.setBizNo(bill.getBillNo());
            log.setChangeQty(qty);
            log.setAfterStockQty(safeQty(stock.getStockQty()));
            log.setCreateTime(now);
            stockLogRepository.save(log);

            d.setRealQty(qty);
            inboundDetailRepository.save(d);

            PurOrderDetail od = orderDetailByProductId.get(d.getProductId());
            if (od == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "商品不在采购单内: productId=" + d.getProductId());
            }
            BigDecimal nextInQty = safeQty(od.getInQty()).add(qty);
            if (nextInQty.compareTo(safeQty(od.getQty())) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "入库后数量超过采购数量: productId=" + d.getProductId() + ", inQty=" + nextInQty + ", ordered=" + od.getQty());
            }
            od.setInQty(nextInQty);
            orderDetailRepository.save(od);
        }

        boolean allCompleted = true;
        for (PurOrderDetail od : orderDetails) {
            BigDecimal ordered = safeQty(od.getQty());
            BigDecimal inQty = safeQty(od.getInQty());
            if (inQty.compareTo(ordered) < 0) {
                allCompleted = false;
                break;
            }
        }
        order.setStatus(allCompleted ? ORDER_STATUS_COMPLETED : ORDER_STATUS_PARTIAL);
        orderRepository.save(order);

        inbound.setStatus(INBOUND_STATUS_COMPLETED);
        inbound.setWmsBillId(bill.getId());
        inbound.setWmsBillNo(bill.getBillNo());
        inbound.setExecuteBy(trimToNull(operator));
        inbound.setExecuteTime(now);
        inboundRepository.save(inbound);

        return new PurInboundExecuteResponse(
                inbound.getId(),
                inbound.getInboundNo(),
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                bill.getId(),
                bill.getBillNo());
    }

    /**
     * Purchase inbound reversal/red冲: rollback stock and purchase inbound progress created by IQC-pass execution.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Only completed inbounds can be reversed.</li>
     *   <li>If the inbound is already referenced by an AP bill (pur_ap_doc_ref), reversal is forbidden.</li>
     *   <li>Idempotent: repeated reverse returns the same reversal WMS bill.</li>
     * </ul>
     */
    @Transactional
    public PurInboundReverseResponse reverse(Long inboundId, String operator) {
        if (inboundId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");

        PurInbound inbound = inboundRepository.findByIdForUpdate(inboundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "入库单不存在"));

        if (Objects.equals(inbound.getStatus(), INBOUND_STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库单已作废，不能冲销");
        }
        if (!Objects.equals(inbound.getStatus(), INBOUND_STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅已完成的入库单允许冲销");
        }
        if (inbound.getWmsBillId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "入库单缺少关联 WMS 单，无法冲销");
        }

        if (Objects.equals(inbound.getReverseStatus(), REVERSE_STATUS_REVERSED) || Objects.equals(inbound.getStatus(), INBOUND_STATUS_REVERSED)) {
            PurOrderRepository.PurOrderRow orderRow = orderRepository.getRow(inbound.getOrderId());
            return new PurInboundReverseResponse(
                    inbound.getId(),
                    inbound.getInboundNo(),
                    inbound.getStatus(),
                    inbound.getOrderId(),
                    inbound.getOrderNo(),
                    orderRow == null ? null : orderRow.getStatus(),
                    inbound.getReverseWmsBillId(),
                    inbound.getReverseWmsBillNo());
        }

        if (docRefRepository.findFirstByDocTypeAndDocId(DOC_TYPE_INBOUND, inbound.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该入库批次已进入对账单，禁止冲销");
        }

        WmsIoBill existing = ioBillRepository.findFirstByBizIdAndType(inbound.getWmsBillId(), WMS_BILL_TYPE_STOCK_OUT).orElse(null);
        if (existing != null) {
            applyReverseRecord(inbound, existing, operator);
            PurOrderRepository.PurOrderRow orderRow = orderRepository.getRow(inbound.getOrderId());
            return new PurInboundReverseResponse(
                    inbound.getId(),
                    inbound.getInboundNo(),
                    inbound.getStatus(),
                    inbound.getOrderId(),
                    inbound.getOrderNo(),
                    orderRow == null ? null : orderRow.getStatus(),
                    existing.getId(),
                    existing.getBillNo());
        }

        WmsIoBill origBill = ioBillRepository.findByIdForUpdate(inbound.getWmsBillId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联 WMS 单不存在"));
        if (!Objects.equals(origBill.getType(), WMS_BILL_TYPE_PURCHASE_IN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联 WMS 单类型异常，无法冲销");
        }
        if (!Objects.equals(origBill.getStatus(), WMS_BILL_STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联 WMS 单未完成，无法冲销");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(inbound.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        PurOrder order = orderRepository.findByIdForUpdate(inbound.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "采购单不存在"));
        if (Objects.equals(order.getStatus(), ORDER_STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "采购单已作废，不能冲销入库");
        }

        List<PurOrderDetail> orderDetails = orderDetailRepository.findByOrderIdOrderByIdAsc(order.getId());
        Map<Long, PurOrderDetail> orderDetailByProductId = new HashMap<>();
        for (PurOrderDetail d : orderDetails) {
            orderDetailByProductId.put(d.getProductId(), d);
        }

        List<PurInboundDetail> inboundDetails = inboundDetailRepository.findByInboundIdOrderByIdAsc(inbound.getId());
        if (inboundDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库单无明细");
        }

        // Precheck: ensure available stock is sufficient for reversal (stock out).
        for (PurInboundDetail d : inboundDetails) {
            BigDecimal qty = safeQty(d.getRealQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), d.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock not found"));
            BigDecimal stockQty = safeQty(stock.getStockQty());
            BigDecimal lockedQty = safeQty(stock.getLockedQty());
            validateStockInvariant(stockQty, lockedQty);
            BigDecimal available = stockQty.subtract(lockedQty);
            if (available.compareTo(qty) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "insufficient stock for reversal (productId=" + d.getProductId() + ", available=" + available + ", required=" + qty + ")");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        WmsIoBill reversal = new WmsIoBill();
        reversal.setBillNo(generateBillNo("RPI"));
        reversal.setType(WMS_BILL_TYPE_STOCK_OUT);
        reversal.setBizId(origBill.getId());
        reversal.setBizNo(origBill.getBillNo());
        reversal.setWarehouseId(wh.getId());
        reversal.setStatus(WMS_BILL_STATUS_COMPLETED);
        reversal.setRemark("冲销采购入库: " + inbound.getInboundNo());
        reversal.setCreateBy(trimToNull(operator));
        reversal.setCreateTime(now);
        try {
            reversal = ioBillRepository.saveAndFlush(reversal);
        } catch (DataIntegrityViolationException e) {
            existing = ioBillRepository.findFirstByBizIdAndType(inbound.getWmsBillId(), WMS_BILL_TYPE_STOCK_OUT).orElse(null);
            if (existing != null) {
                applyReverseRecord(inbound, existing, operator);
                return new PurInboundReverseResponse(
                        inbound.getId(),
                        inbound.getInboundNo(),
                        inbound.getStatus(),
                        inbound.getOrderId(),
                        inbound.getOrderNo(),
                        order.getStatus(),
                        existing.getId(),
                        existing.getBillNo());
            }
            throw e;
        }

        for (PurInboundDetail d : inboundDetails) {
            BigDecimal qty = safeQty(d.getRealQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            WmsIoBillDetail rd = new WmsIoBillDetail();
            rd.setBillId(reversal.getId());
            rd.setProductId(d.getProductId());
            rd.setQty(qty);
            rd.setRealQty(qty);
            ioBillDetailRepository.save(rd);

            WmsStock stock = deductStockWithRetry(wh.getId(), d.getProductId(), qty);

            WmsStockLog log = new WmsStockLog();
            log.setWarehouseId(wh.getId());
            log.setProductId(d.getProductId());
            log.setBizType("PURCHASE_IN_REVERSE");
            log.setBizNo(reversal.getBillNo());
            log.setChangeQty(qty.negate());
            log.setAfterStockQty(safeQty(stock.getStockQty()));
            log.setCreateTime(now);
            stockLogRepository.save(log);

            PurOrderDetail od = orderDetailByProductId.get(d.getProductId());
            if (od == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "商品不在采购单内: productId=" + d.getProductId());
            }
            BigDecimal nextInQty = safeQty(od.getInQty()).subtract(qty);
            if (nextInQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "冲销后入库进度为负: productId=" + d.getProductId());
            }
            od.setInQty(nextInQty);
            orderDetailRepository.save(od);
        }

        boolean anyIn = false;
        boolean allCompleted = true;
        for (PurOrderDetail od : orderDetails) {
            BigDecimal ordered = safeQty(od.getQty());
            BigDecimal inQty = safeQty(od.getInQty());
            if (inQty.compareTo(BigDecimal.ZERO) > 0) anyIn = true;
            if (inQty.compareTo(ordered) < 0) allCompleted = false;
        }
        if (allCompleted) {
            order.setStatus(ORDER_STATUS_COMPLETED);
        } else if (anyIn) {
            order.setStatus(ORDER_STATUS_PARTIAL);
        } else {
            order.setStatus(ORDER_STATUS_AUDITED);
        }
        orderRepository.save(order);

        applyReverseRecord(inbound, reversal, operator);

        return new PurInboundReverseResponse(
                inbound.getId(),
                inbound.getInboundNo(),
                inbound.getStatus(),
                inbound.getOrderId(),
                inbound.getOrderNo(),
                order.getStatus(),
                reversal.getId(),
                reversal.getBillNo());
    }

    private void applyReverseRecord(PurInbound inbound, WmsIoBill reversal, String operator) {
        LocalDateTime now = LocalDateTime.now();
        inbound.setReverseStatus(REVERSE_STATUS_REVERSED);
        inbound.setReverseBy(trimToNull(operator));
        inbound.setReverseTime(now);
        inbound.setReverseWmsBillId(reversal.getId());
        inbound.setReverseWmsBillNo(reversal.getBillNo());
        inbound.setStatus(INBOUND_STATUS_REVERSED);
        inboundRepository.save(inbound);
    }

    private void ensureStockRowExists(Long warehouseId, Long productId) {
        LocalDateTime now = LocalDateTime.now();
        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElse(null);
        if (stock != null) return;

        WmsStock created = new WmsStock();
        created.setWarehouseId(warehouseId);
        created.setProductId(productId);
        created.setStockQty(BigDecimal.ZERO);
        created.setLockedQty(BigDecimal.ZERO);
        created.setVersion(0);
        created.setUpdateTime(now);
        validateStockInvariant(created.getStockQty(), created.getLockedQty());
        try {
            stockRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            // Another txn created it; ignore.
        }
    }

    private WmsStockQc increaseQcWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return increaseQcOnce(warehouseId, productId, qty);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private WmsStockQc increaseQcOnce(Long warehouseId, Long productId, BigDecimal qty) {
        ensureStockRowExists(warehouseId, productId);

        LocalDateTime now = LocalDateTime.now();
        WmsStockQc qc = stockQcRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElse(null);
        if (qc == null) {
            WmsStockQc created = new WmsStockQc();
            created.setWarehouseId(warehouseId);
            created.setProductId(productId);
            created.setQcQty(qty);
            created.setVersion(0);
            created.setUpdateTime(now);
            validateQcInvariant(created.getQcQty());
            try {
                return stockQcRepository.saveAndFlush(created);
            } catch (DataIntegrityViolationException e) {
                qc = stockQcRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                        .orElseThrow(() -> e);
            }
        }

        BigDecimal qcQty = safeQty(qc.getQcQty());
        validateQcInvariant(qcQty);
        qc.setQcQty(qcQty.add(qty));
        qc.setUpdateTime(now);
        validateQcInvariant(qc.getQcQty());
        return stockQcRepository.saveAndFlush(qc);
    }

    private WmsStockQc decreaseQcWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return decreaseQcOnce(warehouseId, productId, qty);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private WmsStockQc decreaseQcOnce(Long warehouseId, Long productId, BigDecimal qty) {
        LocalDateTime now = LocalDateTime.now();
        WmsStockQc qc = stockQcRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElse(null);
        if (qc == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "QC stock bucket missing (warehouseId=" + warehouseId + ", productId=" + productId + ")");
        }

        BigDecimal qcQty = safeQty(qc.getQcQty());
        validateQcInvariant(qcQty);
        if (qcQty.compareTo(qty) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "QC qty insufficient (warehouseId=" + warehouseId + ", productId=" + productId + ", qcQty=" + qcQty + ", required=" + qty + ")");
        }

        qc.setQcQty(qcQty.subtract(qty));
        qc.setUpdateTime(now);
        validateQcInvariant(qc.getQcQty());
        return stockQcRepository.saveAndFlush(qc);
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

    private static void validateLines(List<PurInboundCreateLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }
        Set<Long> seen = new HashSet<>();
        for (PurInboundCreateLineRequest line : lines) {
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
        }
    }

    private static void validateNewOrderLines(List<PurInboundNewOrderLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }
        Set<Long> seen = new HashSet<>();
        boolean hasInbound = false;
        for (PurInboundNewOrderLineRequest line : lines) {
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
            BigDecimal inboundQty = safeQty(line.inboundQty());
            if (inboundQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inboundQty must be >= 0");
            }
            if (inboundQty.compareTo(qty) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "inboundQty must be <= qty (productId=" + line.productId() + ")");
            }
            if (inboundQty.compareTo(BigDecimal.ZERO) > 0) {
                hasInbound = true;
            }
        }
        if (!hasInbound) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one inboundQty must be > 0");
        }
    }

    private static PurInboundResponse toResponse(PurInboundRepository.PurInboundRow row) {
        if (row == null) return null;
        return new PurInboundResponse(
                row.getId(),
                row.getInboundNo(),
                row.getRequestNo(),
                row.getOrderId(),
                row.getOrderNo(),
                row.getSupplierId(),
                row.getSupplierCode(),
                row.getSupplierName(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getStatus(),
                row.getQcStatus(),
                row.getQcBy(),
                row.getQcTime(),
                row.getQcRemark(),
                row.getWmsBillId(),
                row.getWmsBillNo(),
                row.getReverseStatus(),
                row.getReverseBy(),
                row.getReverseTime(),
                row.getReverseWmsBillId(),
                row.getReverseWmsBillNo(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getExecuteBy(),
                row.getExecuteTime());
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

    private static void validateQcInvariant(BigDecimal qcQty) {
        BigDecimal q = safeQty(qcQty);
        if (q.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid QC stock state: qc_qty < 0");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String generateInboundNo() {
        return generateBillNo("PIN");
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
