package com.ordererp.backend.purchase.service;

import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.purchase.dto.PurInboundCreateLineRequest;
import com.ordererp.backend.purchase.dto.PurInboundCreateRequest;
import com.ordererp.backend.purchase.dto.PurInboundDetailResponse;
import com.ordererp.backend.purchase.dto.PurInboundExecuteResponse;
import com.ordererp.backend.purchase.dto.PurInboundItemResponse;
import com.ordererp.backend.purchase.dto.PurInboundResponse;
import com.ordererp.backend.purchase.entity.PurInbound;
import com.ordererp.backend.purchase.entity.PurInboundDetail;
import com.ordererp.backend.purchase.entity.PurOrder;
import com.ordererp.backend.purchase.entity.PurOrderDetail;
import com.ordererp.backend.purchase.repository.PurInboundDetailRepository;
import com.ordererp.backend.purchase.repository.PurInboundRepository;
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

    private static final int INBOUND_STATUS_COMPLETED = 2;

    private static final int WMS_BILL_TYPE_PURCHASE_IN = 1;
    private static final int WMS_BILL_STATUS_COMPLETED = 2;

    private final PurInboundRepository inboundRepository;
    private final PurInboundDetailRepository inboundDetailRepository;
    private final PurOrderRepository orderRepository;
    private final PurOrderDetailRepository orderDetailRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockLogRepository stockLogRepository;

    public PurInboundService(PurInboundRepository inboundRepository, PurInboundDetailRepository inboundDetailRepository,
            PurOrderRepository orderRepository, PurOrderDetailRepository orderDetailRepository,
            BaseWarehouseRepository warehouseRepository, BaseProductRepository productRepository,
            WmsIoBillRepository ioBillRepository, WmsIoBillDetailRepository ioBillDetailRepository,
            WmsStockRepository stockRepository, WmsStockLogRepository stockLogRepository) {
        this.inboundRepository = inboundRepository;
        this.inboundDetailRepository = inboundDetailRepository;
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockRepository = stockRepository;
        this.stockLogRepository = stockLogRepository;
    }

    public Page<PurInboundResponse> page(String keyword, Pageable pageable) {
        return inboundRepository.pageRows(trimToNull(keyword), pageable).map(PurInboundService::toResponse);
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
     * 采购分批入库（从采购订单发起）：创建一张入库单并立刻执行。
     *
     * <p>幂等语义：</p>
     * <ul>
     *   <li>由客户端提供 requestNo（UUID）。重复提交相同 requestNo 必须返回同一张入库单，不重复加库存。</li>
     *   <li>实现方式：pur_inbound.request_no 唯一约束 + 并发下捕获唯一键冲突回查 existing。</li>
     * </ul>
     */
    @Transactional
    public PurInboundExecuteResponse createAndExecuteFromOrder(Long orderId, PurInboundCreateRequest request, String operator) {
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
        inbound.setStatus(INBOUND_STATUS_COMPLETED);
        inbound.setRemark(trimToNull(request.remark()));
        inbound.setCreateBy(trimToNull(operator));
        inbound.setCreateTime(now);
        inbound.setExecuteBy(trimToNull(operator));
        inbound.setExecuteTime(now);

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

        // 执行入库：写 WMS 单据 + 加库存 + 写流水 + 回写采购单进度
        WmsIoBill bill = new WmsIoBill();
        bill.setBillNo(generateBillNo("PI"));
        bill.setType(WMS_BILL_TYPE_PURCHASE_IN);
        bill.setBizId(null); // biz_id 预留给 WMS 内部冲销等语义；采购域用 biz_no 做追溯
        bill.setBizNo(inbound.getInboundNo());
        bill.setWarehouseId(wh.getId());
        bill.setStatus(WMS_BILL_STATUS_COMPLETED);
        bill.setRemark("采购入库: " + order.getOrderNo() + " / " + inbound.getInboundNo());
        bill.setCreateBy(trimToNull(operator));
        bill.setCreateTime(now);
        bill = ioBillRepository.saveAndFlush(bill);

        List<PurInboundDetail> inboundDetails = inboundDetailRepository.findByInboundIdOrderByIdAsc(inbound.getId());
        if (inboundDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入库单没有明细");
        }

        for (PurInboundDetail d : inboundDetails) {
            BigDecimal qty = safeQty(d.getPlanQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

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

        inbound.setWmsBillId(bill.getId());
        inbound.setWmsBillNo(bill.getBillNo());
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
                row.getWmsBillId(),
                row.getWmsBillNo(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getExecuteBy(),
                row.getExecuteTime());
    }

    private static BigDecimal safeQty(BigDecimal v) {
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

    private static String generateInboundNo() {
        return generateBillNo("PIN");
    }

    private static String generateBillNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return prefix + ts + "-" + rand;
    }
}
