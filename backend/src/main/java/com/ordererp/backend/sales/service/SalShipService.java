package com.ordererp.backend.sales.service;

import com.ordererp.backend.sales.dto.SalShipReverseResponse;
import com.ordererp.backend.sales.dto.SalShipDetailResponse;
import com.ordererp.backend.sales.dto.SalShipItemResponse;
import com.ordererp.backend.sales.dto.SalShipResponse;
import com.ordererp.backend.sales.entity.SalOrder;
import com.ordererp.backend.sales.entity.SalOrderDetail;
import com.ordererp.backend.sales.entity.SalShip;
import com.ordererp.backend.sales.entity.SalShipDetail;
import com.ordererp.backend.sales.repository.SalOrderDetailRepository;
import com.ordererp.backend.sales.repository.SalOrderRepository;
import com.ordererp.backend.sales.repository.SalShipDetailRepository;
import com.ordererp.backend.sales.repository.SalShipRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalShipService {
    private static final int REVERSE_STATUS_NONE = 0;
    private static final int REVERSE_STATUS_REVERSED = 1;

    private static final int ORDER_STATUS_AUDITED = 2;
    private static final int ORDER_STATUS_PARTIAL_SHIPPED = 3;
    private static final int ORDER_STATUS_SHIPPED = 4;
    private static final int ORDER_STATUS_CANCELED = 9;

    private static final int WMS_BILL_STATUS_COMPLETED = 2;
    private static final int WMS_BILL_TYPE_STOCK_IN = 3;
    private static final int WMS_BILL_TYPE_SALES_OUT = 5;

    private final SalShipRepository shipRepository;
    private final SalShipDetailRepository shipDetailRepository;
    private final SalOrderRepository orderRepository;
    private final SalOrderDetailRepository orderDetailRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockLogRepository stockLogRepository;

    public SalShipService(SalShipRepository shipRepository, SalShipDetailRepository shipDetailRepository,
            SalOrderRepository orderRepository, SalOrderDetailRepository orderDetailRepository,
            WmsIoBillRepository ioBillRepository, WmsIoBillDetailRepository ioBillDetailRepository,
            WmsStockRepository stockRepository, WmsStockLogRepository stockLogRepository) {
        this.shipRepository = shipRepository;
        this.shipDetailRepository = shipDetailRepository;
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockRepository = stockRepository;
        this.stockLogRepository = stockLogRepository;
    }

    public Page<SalShipResponse> page(String keyword, Long customerId, Long warehouseId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return shipRepository.pageRows(trimToNull(keyword), customerId, warehouseId, startDate, endDate, pageable)
                .map(SalShipService::toShipResponse);
    }

    public SalShipDetailResponse detail(Long shipId) {
        var header = shipRepository.getRow(shipId);
        if (header == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "销售出库单不存在");
        var items = shipDetailRepository.listRows(shipId).stream()
                .map(r -> new SalShipItemResponse(
                        r.getId(),
                        r.getShipId(),
                        r.getOrderId(),
                        r.getOrderDetailId(),
                        r.getProductId(),
                        r.getProductCode(),
                        r.getProductName(),
                        r.getUnit(),
                        r.getQty()))
                .toList();
        return new SalShipDetailResponse(toShipResponse(header), items);
    }

    /**
     * 发货冲销（纠正错误发货）：回滚库存 + 恢复锁库 + 回滚 shipped_qty。
     *
     * <p>约束：</p>
     * <ul>
     *   <li>仅已生成销售出库（wmsBillId）且 WMS 单已完成的批次允许冲销。</li>
     *   <li>幂等：重复冲销返回同一张冲销 WMS 单，不能重复加库存/重复写流水。</li>
     * </ul>
     */
    @Transactional
    public SalShipReverseResponse reverse(Long shipId, String operator) {
        if (shipId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");

        SalShip ship = shipRepository.findByIdForUpdate(shipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售出库单不存在"));

        if (ship.getWmsBillId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "销售出库单缺少关联 WMS 单，无法冲销");
        }

        if (ship.getReverseWmsBillId() != null && (ship.getReverseStatus() != null && ship.getReverseStatus() == REVERSE_STATUS_REVERSED)) {
            return new SalShipReverseResponse(ship.getId(), ship.getShipNo(), ship.getReverseStatus(), ship.getReverseWmsBillId(), ship.getReverseWmsBillNo());
        }

        // Idempotency: reversal WMS bill is keyed by original wmsBillId + type(STOCK_IN)
        WmsIoBill existing = ioBillRepository.findFirstByBizIdAndType(ship.getWmsBillId(), WMS_BILL_TYPE_STOCK_IN).orElse(null);
        if (existing != null) {
            applyReverseRecord(ship, existing, operator);
            return new SalShipReverseResponse(ship.getId(), ship.getShipNo(), ship.getReverseStatus(), existing.getId(), existing.getBillNo());
        }

        WmsIoBill origBill = ioBillRepository.findByIdForUpdate(ship.getWmsBillId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联 WMS 单不存在"));
        if (!Integer.valueOf(WMS_BILL_TYPE_SALES_OUT).equals(origBill.getType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联 WMS 单类型异常，无法冲销");
        }
        if (!Integer.valueOf(WMS_BILL_STATUS_COMPLETED).equals(origBill.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联 WMS 单未完成，无法冲销");
        }

        SalOrder order = orderRepository.findByIdForUpdate(ship.getOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在"));
        if (Integer.valueOf(ORDER_STATUS_CANCELED).equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单已作废，禁止冲销发货");
        }
        if (!Integer.valueOf(ORDER_STATUS_AUDITED).equals(order.getStatus())
                && !Integer.valueOf(ORDER_STATUS_PARTIAL_SHIPPED).equals(order.getStatus())
                && !Integer.valueOf(ORDER_STATUS_SHIPPED).equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单状态不允许冲销发货");
        }

        List<SalShipDetail> shipDetails = shipDetailRepository.findByShipIdOrderByIdAsc(ship.getId());
        if (shipDetails == null || shipDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售出库单无明细，无法冲销");
        }

        List<SalOrderDetail> orderDetails = orderDetailRepository.findByOrderIdOrderByIdAsc(order.getId());
        Map<Long, SalOrderDetail> orderDetailById = new HashMap<>();
        for (SalOrderDetail d : orderDetails) {
            if (d == null) continue;
            orderDetailById.put(d.getId(), d);
        }

        LocalDateTime now = LocalDateTime.now();

        // Create reversal WMS bill (stock-in), idempotent by origWmsBillId + type
        WmsIoBill reversal = new WmsIoBill();
        reversal.setBillNo(generateReverseWmsBillNo());
        reversal.setType(WMS_BILL_TYPE_STOCK_IN);
        reversal.setBizId(origBill.getId());
        reversal.setBizNo(origBill.getBillNo());
        reversal.setWarehouseId(origBill.getWarehouseId());
        reversal.setStatus(WMS_BILL_STATUS_COMPLETED);
        reversal.setRemark("冲销销售出库: " + ship.getShipNo());
        reversal.setCreateBy(trimToNull(operator));
        reversal.setCreateTime(now);
        try {
            reversal = ioBillRepository.saveAndFlush(reversal);
        } catch (DataIntegrityViolationException e) {
            existing = ioBillRepository.findFirstByBizIdAndType(origBill.getId(), WMS_BILL_TYPE_STOCK_IN).orElse(null);
            if (existing != null) {
                applyReverseRecord(ship, existing, operator);
                return new SalShipReverseResponse(ship.getId(), ship.getShipNo(), ship.getReverseStatus(), existing.getId(), existing.getBillNo());
            }
            throw e;
        }

        List<WmsIoBillDetail> reversalDetails = new ArrayList<>();
        for (SalShipDetail sd : shipDetails) {
            if (sd == null) continue;
            BigDecimal qty = safeQty(sd.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            SalOrderDetail od = orderDetailById.get(sd.getOrderDetailId());
            if (od == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "订单明细不存在: orderDetailId=" + sd.getOrderDetailId());
            }
            if (!od.getProductId().equals(sd.getProductId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "商品不匹配: orderDetailId=" + sd.getOrderDetailId());
            }

            // Inventory: stock_qty += qty, locked_qty += qty (restore reservation)
            WmsStock stock = increaseAndLockWithRetry(order.getWarehouseId(), sd.getProductId(), qty);
            WmsStockLog log = new WmsStockLog();
            log.setWarehouseId(order.getWarehouseId());
            log.setProductId(sd.getProductId());
            log.setBizType("SALES_OUT_REVERSE");
            log.setBizNo(reversal.getBillNo());
            log.setChangeQty(qty);
            log.setAfterStockQty(safeQty(stock.getStockQty()));
            log.setCreateTime(now);
            stockLogRepository.save(log);

            BigDecimal nextShipped = safeQty(od.getShippedQty()).subtract(qty);
            if (nextShipped.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "冲销后已发数量为负: orderDetailId=" + od.getId());
            }
            od.setShippedQty(nextShipped);
            orderDetailRepository.save(od);

            WmsIoBillDetail rd = new WmsIoBillDetail();
            rd.setBillId(reversal.getId());
            rd.setProductId(sd.getProductId());
            rd.setQty(qty);
            rd.setRealQty(qty);
            reversalDetails.add(rd);
        }
        if (!reversalDetails.isEmpty()) {
            ioBillDetailRepository.saveAll(reversalDetails);
        }

        // Recompute order status after reversal
        boolean anyShipped = false;
        boolean allShipped = true;
        for (SalOrderDetail d : orderDetails) {
            BigDecimal ordered = safeQty(d.getQty());
            BigDecimal shipped = safeQty(d.getShippedQty());
            if (shipped.compareTo(BigDecimal.ZERO) > 0) anyShipped = true;
            if (shipped.compareTo(ordered) < 0) allShipped = false;
        }

        if (allShipped) {
            order.setStatus(ORDER_STATUS_SHIPPED);
        } else if (anyShipped) {
            order.setStatus(ORDER_STATUS_PARTIAL_SHIPPED);
            order.setShipBy(null);
            order.setShipTime(null);
        } else {
            order.setStatus(ORDER_STATUS_AUDITED);
            order.setShipBy(null);
            order.setShipTime(null);
        }
        orderRepository.saveAndFlush(order);

        applyReverseRecord(ship, reversal, operator);
        return new SalShipReverseResponse(ship.getId(), ship.getShipNo(), ship.getReverseStatus(), reversal.getId(), reversal.getBillNo());
    }

    private static SalShipResponse toShipResponse(SalShipRepository.ShipRow r) {
        if (r == null) return null;
        return new SalShipResponse(
                r.getId(),
                r.getShipNo(),
                r.getOrderId(),
                r.getOrderNo(),
                r.getCustomerId(),
                r.getCustomerCode(),
                r.getCustomerName(),
                r.getWarehouseId(),
                r.getWarehouseName(),
                r.getShipTime(),
                r.getTotalQty(),
                r.getWmsBillId(),
                r.getWmsBillNo(),
                r.getReverseStatus(),
                r.getReverseBy(),
                r.getReverseTime(),
                r.getReverseWmsBillId(),
                r.getReverseWmsBillNo(),
                r.getCreateBy(),
                r.getCreateTime());
    }

    private void applyReverseRecord(SalShip ship, WmsIoBill reversal, String operator) {
        LocalDateTime now = LocalDateTime.now();
        ship.setReverseStatus(REVERSE_STATUS_REVERSED);
        ship.setReverseBy(trimToNull(operator));
        ship.setReverseTime(now);
        ship.setReverseWmsBillId(reversal.getId());
        ship.setReverseWmsBillNo(reversal.getBillNo());
        shipRepository.save(ship);
    }

    private WmsStock increaseAndLockWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return increaseAndLockOnce(warehouseId, productId, qty);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private WmsStock increaseAndLockOnce(Long warehouseId, Long productId, BigDecimal qty) {
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该仓库无此商品库存记录"));
        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该仓库无此商品库存记录"));
        BigDecimal stockQty = safeQty(stock.getStockQty());
        BigDecimal lockedQty = safeQty(stock.getLockedQty());
        validateStockInvariant(stockQty, lockedQty);
        stock.setStockQty(stockQty.add(qty));
        stock.setLockedQty(lockedQty.add(qty));
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

    private static BigDecimal safeQty(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String generateReverseWmsBillNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "RSI" + ts + "-" + rand;
    }
}
