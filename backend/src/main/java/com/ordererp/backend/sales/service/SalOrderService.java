package com.ordererp.backend.sales.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.sales.dto.SalOrderCreateRequest;
import com.ordererp.backend.sales.dto.SalOrderDetailResponse;
import com.ordererp.backend.sales.dto.SalOrderItemResponse;
import com.ordererp.backend.sales.dto.SalOrderOptionResponse;
import com.ordererp.backend.sales.dto.SalOrderResponse;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalOrderService {
    private static final int PARTNER_TYPE_CUSTOMER = 2;

    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_AUDITED = 2;
    private static final int STATUS_PARTIAL_SHIPPED = 3;
    private static final int STATUS_SHIPPED = 4;
    private static final int STATUS_CANCELED = 9;

    private static final int WMS_BILL_TYPE_SALES_OUT = 5;
    private static final int WMS_BILL_STATUS_COMPLETED = 2;

    private final SalOrderRepository orderRepository;
    private final SalOrderDetailRepository detailRepository;
    private final SalShipRepository shipRepository;
    private final SalShipDetailRepository shipDetailRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;
    private final WmsStockRepository stockRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockLogRepository stockLogRepository;

    public SalOrderService(SalOrderRepository orderRepository, SalOrderDetailRepository detailRepository,
            SalShipRepository shipRepository, SalShipDetailRepository shipDetailRepository,
            BasePartnerRepository partnerRepository, BaseWarehouseRepository warehouseRepository, BaseProductRepository productRepository,
            WmsStockRepository stockRepository,
            WmsIoBillRepository ioBillRepository, WmsIoBillDetailRepository ioBillDetailRepository,
            WmsStockLogRepository stockLogRepository) {
        this.orderRepository = orderRepository;
        this.detailRepository = detailRepository;
        this.shipRepository = shipRepository;
        this.shipDetailRepository = shipDetailRepository;
        this.partnerRepository = partnerRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockLogRepository = stockLogRepository;
    }

    public Page<SalOrderResponse> page(String keyword, Long customerId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return orderRepository.pageRows(trimToNull(keyword), customerId, startDate, endDate, pageable)
                .map(SalOrderService::toResponse);
    }

    public List<SalOrderOptionResponse> options(String keyword, Integer limit) {
        int size = limit == null ? 200 : Math.max(1, Math.min(500, limit));
        return orderRepository.optionRows(trimToNull(keyword), size).stream()
                .map(r -> new SalOrderOptionResponse(
                        r.getId(),
                        r.getOrderNo(),
                        r.getCustomerId(),
                        r.getCustomerCode(),
                        r.getCustomerName(),
                        r.getWarehouseId(),
                        r.getWarehouseName(),
                        r.getOrderDate(),
                        r.getStatus()))
                .toList();
    }

    public SalOrderResponse get(Long id) {
        SalOrderRepository.SalOrderDetailRow row = orderRepository.getDetailRow(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在");
        return toResponse(row);
    }

    public SalOrderDetailResponse detail(Long id) {
        SalOrderRepository.SalOrderDetailRow row = orderRepository.getDetailRow(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在");

        List<SalOrderItemResponse> items = detailRepository.listItemRows(id).stream()
                .map(r -> new SalOrderItemResponse(
                        r.getId(),
                        r.getProductId(),
                        r.getProductCode(),
                        r.getProductName(),
                        r.getUnit(),
                        r.getPrice(),
                        r.getQty(),
                        r.getShippedQty(),
                        r.getAmount()))
                .toList();

        return new SalOrderDetailResponse(
                row.getId(),
                row.getOrderNo(),
                row.getCustomerId(),
                row.getCustomerCode(),
                row.getCustomerName(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getOrderDate(),
                row.getTotalAmount(),
                row.getStatus(),
                row.getRemark(),
                row.getWmsBillId(),
                row.getWmsBillNo(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getAuditBy(),
                row.getAuditTime(),
                row.getShipBy(),
                row.getShipTime(),
                row.getCancelBy(),
                row.getCancelTime(),
                items);
    }

    public List<SalShipResponse> listShips(Long orderId) {
        return shipRepository.listRows(orderId).stream().map(SalOrderService::toShipResponse).toList();
    }

    public SalShipDetailResponse shipDetail(Long shipId) {
        var header = shipRepository.getRow(shipId);
        if (header == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "发货批次不存在");
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

    @Transactional
    public SalOrderResponse create(SalOrderCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }

        BasePartner customer = partnerRepository.findByIdAndDeleted(request.customerId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户不存在"));
        if (!Objects.equals(customer.getType(), PARTNER_TYPE_CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来单位不是客户(type=2)");
        }
        if (customer.getStatus() != null && customer.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户已禁用");
        }

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(request.warehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        Set<Long> seenProducts = new HashSet<>();
        List<SalOrderDetail> details = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var line : request.lines()) {
            if (line == null) continue;
            if (line.productId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
            if (!seenProducts.add(line.productId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "重复商品");
            BigDecimal qty = safeQty(line.qty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be > 0");

            BaseProduct p = productRepository.findByIdAndDeleted(line.productId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品不存在"));
            if (p.getStatus() != null && p.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已禁用");
            }

            BigDecimal price = line.price();
            BigDecimal amount = null;
            if (price != null) {
                if (price.compareTo(BigDecimal.ZERO) < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "price must be >= 0");
                amount = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                totalAmount = totalAmount.add(amount);
            }

            SalOrderDetail d = new SalOrderDetail();
            d.setProductId(p.getId());
            d.setProductCode(p.getProductCode());
            d.setProductName(p.getProductName());
            d.setUnit(p.getUnit());
            d.setQty(qty);
            d.setShippedQty(BigDecimal.ZERO);
            d.setPrice(price);
            d.setAmount(amount);
            details.add(d);
        }

        LocalDateTime now = LocalDateTime.now();
        SalOrder o = new SalOrder();
        o.setOrderNo(generateOrderNo());
        o.setCustomerId(customer.getId());
        o.setCustomerCode(customer.getPartnerCode());
        o.setCustomerName(customer.getPartnerName());
        o.setWarehouseId(wh.getId());
        o.setOrderDate(request.orderDate() == null ? LocalDate.now() : request.orderDate());
        o.setTotalAmount(totalAmount);
        o.setStatus(STATUS_DRAFT);
        o.setRemark(trimToNull(request.remark()));
        o.setCreateBy(trimToNull(operator));
        o.setCreateTime(now);
        o = orderRepository.saveAndFlush(o);

        Long orderId = o.getId();
        for (SalOrderDetail d : details) {
            d.setOrderId(orderId);
        }
        detailRepository.saveAll(details);

        return toResponse(orderRepository.getDetailRow(orderId));
    }

    /**
     * 审核：锁定库存（locked_qty += qty）。重复审核幂等返回。
     */
    @Transactional
    public SalOrderResponse audit(Long id, String operator) {
        SalOrder o = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在"));
        if (Objects.equals(o.getStatus(), STATUS_AUDITED) || Objects.equals(o.getStatus(), STATUS_PARTIAL_SHIPPED) || Objects.equals(o.getStatus(), STATUS_SHIPPED)) {
            return toResponse(orderRepository.getDetailRow(o.getId()));
        }
        if (Objects.equals(o.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单已作废");
        }
        if (!Objects.equals(o.getStatus(), STATUS_DRAFT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单状态不允许审核");
        }

        List<SalOrderDetail> items = detailRepository.findByOrderIdOrderByIdAsc(o.getId());
        if (items == null || items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单无明细");

        for (SalOrderDetail it : items) {
            if (it == null) continue;
            lockStockWithRetry(o.getWarehouseId(), it.getProductId(), safeQty(it.getQty()));
        }

        o.setStatus(STATUS_AUDITED);
        o.setAuditBy(trimToNull(operator));
        o.setAuditTime(LocalDateTime.now());
        orderRepository.saveAndFlush(o);
        return toResponse(orderRepository.getDetailRow(o.getId()));
    }

    /**
     * 一键全量发货：默认把所有“未发数量”一次发完（会生成一张发货批次单/一张 WMS 销售出库单）。
     *
     * <p>重复发货幂等：若已生成 wmsBillId/wmsBillNo 则直接返回。</p>
     */
    @Transactional
    public SalOrderResponse ship(Long id, String operator) {
        SalOrder o = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在"));
        if (Objects.equals(o.getStatus(), STATUS_SHIPPED)) {
            return toResponse(orderRepository.getDetailRow(o.getId()));
        }
        if (Objects.equals(o.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单已作废");
        }
        if (!Objects.equals(o.getStatus(), STATUS_AUDITED) && !Objects.equals(o.getStatus(), STATUS_PARTIAL_SHIPPED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先审核锁库后再发货");
        }

        List<SalOrderDetail> items = detailRepository.findByOrderIdOrderByIdAsc(o.getId());
        if (items == null || items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单无明细");

        // if already fully shipped, return
        if (items.stream().allMatch(it -> safeQty(it.getQty()).compareTo(safeQty(it.getShippedQty())) <= 0)) {
            o.setStatus(STATUS_SHIPPED);
            if (o.getShipTime() == null) o.setShipTime(LocalDateTime.now());
            if (o.getShipBy() == null) o.setShipBy(trimToNull(operator));
            orderRepository.saveAndFlush(o);
            return toResponse(orderRepository.getDetailRow(o.getId()));
        }

        // Full ship remaining as a batch shipment
        List<ShipLine> lines = new ArrayList<>();
        for (SalOrderDetail it : items) {
            BigDecimal remain = safeQty(it.getQty()).subtract(safeQty(it.getShippedQty()));
            if (remain.compareTo(BigDecimal.ZERO) <= 0) continue;
            lines.add(new ShipLine(it.getId(), it.getProductId(), remain));
        }
        shipBatchInternal(o, items, lines, operator);
        return toResponse(orderRepository.getDetailRow(o.getId()));
    }

    /**
     * 作废：草稿可直接作废；已审核（已锁库）作废需释放锁定库存。
     */
    @Transactional
    public SalOrderResponse cancel(Long id, String operator) {
        SalOrder o = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在"));
        if (Objects.equals(o.getStatus(), STATUS_CANCELED)) return toResponse(orderRepository.getDetailRow(o.getId()));
        if (Objects.equals(o.getStatus(), STATUS_SHIPPED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已发货订单不允许作废");
        }

        if (Objects.equals(o.getStatus(), STATUS_AUDITED)) {
            List<SalOrderDetail> items = detailRepository.findByOrderIdOrderByIdAsc(o.getId());
            for (SalOrderDetail it : items) {
                if (it == null) continue;
                BigDecimal remainLocked = safeQty(it.getQty()).subtract(safeQty(it.getShippedQty()));
                if (remainLocked.compareTo(BigDecimal.ZERO) > 0) {
                    unlockStockWithRetry(o.getWarehouseId(), it.getProductId(), remainLocked);
                }
            }
        } else if (Objects.equals(o.getStatus(), STATUS_PARTIAL_SHIPPED)) {
            // partially shipped: only unlock remaining locked qty
            List<SalOrderDetail> items = detailRepository.findByOrderIdOrderByIdAsc(o.getId());
            for (SalOrderDetail it : items) {
                if (it == null) continue;
                BigDecimal remainLocked = safeQty(it.getQty()).subtract(safeQty(it.getShippedQty()));
                if (remainLocked.compareTo(BigDecimal.ZERO) > 0) {
                    unlockStockWithRetry(o.getWarehouseId(), it.getProductId(), remainLocked);
                }
            }
        } else if (!Objects.equals(o.getStatus(), STATUS_DRAFT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单状态不允许作废");
        }

        o.setStatus(STATUS_CANCELED);
        o.setCancelBy(trimToNull(operator));
        o.setCancelTime(LocalDateTime.now());
        orderRepository.saveAndFlush(o);
        return toResponse(orderRepository.getDetailRow(o.getId()));
    }

    private void lockStockWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                lockStockOnce(warehouseId, productId, qty);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private void lockStockOnce(Long warehouseId, Long productId, BigDecimal qty) {
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return;
        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该仓库无此商品库存记录"));
        BigDecimal stockQty = safeQty(stock.getStockQty());
        BigDecimal lockedQty = safeQty(stock.getLockedQty());
        validateStockInvariant(stockQty, lockedQty);
        BigDecimal available = stockQty.subtract(lockedQty);
        if (available.compareTo(qty) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足，当前可用: " + available);
        }
        stock.setLockedQty(lockedQty.add(qty));
        stockRepository.saveAndFlush(stock);
    }

    private void unlockStockWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                unlockStockOnce(warehouseId, productId, qty);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private void unlockStockOnce(Long warehouseId, Long productId, BigDecimal qty) {
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return;
        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该仓库无此商品库存记录"));
        BigDecimal stockQty = safeQty(stock.getStockQty());
        BigDecimal lockedQty = safeQty(stock.getLockedQty());
        validateStockInvariant(stockQty, lockedQty);
        BigDecimal nextLocked = lockedQty.subtract(qty);
        if (nextLocked.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid stock state: locked_qty < 0");
        }
        stock.setLockedQty(nextLocked);
        stockRepository.saveAndFlush(stock);
    }

    private void deductAndUnlockWithRetry(Long warehouseId, Long productId, BigDecimal qty, String bizNo, LocalDateTime now) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                deductAndUnlockOnce(warehouseId, productId, qty, bizNo, now);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private void deductAndUnlockOnce(Long warehouseId, Long productId, BigDecimal qty, String bizNo, LocalDateTime now) {
        if (qty.compareTo(BigDecimal.ZERO) <= 0) return;
        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该仓库无此商品库存记录"));
        BigDecimal stockQty = safeQty(stock.getStockQty());
        BigDecimal lockedQty = safeQty(stock.getLockedQty());
        validateStockInvariant(stockQty, lockedQty);
        if (lockedQty.compareTo(qty) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "invalid stock state: locked_qty < required");
        }
        if (stockQty.compareTo(qty) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足，当前物理: " + stockQty);
        }

        stock.setStockQty(stockQty.subtract(qty));
        stock.setLockedQty(lockedQty.subtract(qty));
        stockRepository.saveAndFlush(stock);

        WmsStockLog log = new WmsStockLog();
        log.setWarehouseId(warehouseId);
        log.setProductId(productId);
        log.setBizType("SALES_OUT");
        log.setBizNo(trimToNull(bizNo));
        log.setChangeQty(qty.negate());
        log.setAfterStockQty(stock.getStockQty());
        log.setCreateTime(now);
        stockLogRepository.save(log);
    }

    @Transactional
    public void shipBatch(Long orderId, List<ShipLine> lines, String operator) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }

        SalOrder o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售订单不存在"));
        if (Objects.equals(o.getStatus(), STATUS_CANCELED)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单已作废");
        if (!Objects.equals(o.getStatus(), STATUS_AUDITED) && !Objects.equals(o.getStatus(), STATUS_PARTIAL_SHIPPED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单未处于可发货状态");
        }

        List<SalOrderDetail> items = detailRepository.findByOrderIdOrderByIdAsc(o.getId());
        if (items == null || items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单无明细");

        shipBatchInternal(o, items, lines, operator);
    }

    private void shipBatchInternal(SalOrder o, List<SalOrderDetail> items, List<ShipLine> lines, String operator) {
        // Validate lines and compute remain
        Set<Long> seen = new HashSet<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        List<SalShipDetail> shipDetails = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        String shipNo = generateShipNo();
        SalShip ship = new SalShip();
        ship.setShipNo(shipNo);
        ship.setOrderId(o.getId());
        ship.setOrderNo(o.getOrderNo());
        ship.setCustomerId(o.getCustomerId());
        ship.setCustomerCode(o.getCustomerCode());
        ship.setCustomerName(o.getCustomerName());
        ship.setWarehouseId(o.getWarehouseId());
        ship.setShipTime(now);
        ship.setTotalQty(BigDecimal.ZERO);
        ship.setCreateBy(trimToNull(operator));
        ship.setCreateTime(now);
        ship = shipRepository.saveAndFlush(ship);

        // Deduct stock (and unlock) and update shipped_qty
        for (ShipLine line : lines) {
            if (line == null) continue;
            if (line.orderDetailId() == null || line.productId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderDetailId/productId is required");
            }
            if (!seen.add(line.orderDetailId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "重复行");
            BigDecimal shipQty = safeQty(line.qty());
            if (shipQty.compareTo(BigDecimal.ZERO) <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ship qty must be > 0");

            SalOrderDetail it = items.stream().filter(x -> Objects.equals(x.getId(), line.orderDetailId())).findFirst().orElse(null);
            if (it == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order detail not found");
            if (!Objects.equals(it.getProductId(), line.productId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product mismatch");
            }

            BigDecimal remain = safeQty(it.getQty()).subtract(safeQty(it.getShippedQty()));
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "line already shipped");
            }
            if (shipQty.compareTo(remain) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ship qty exceeds remaining");
            }

            // stock: deduct physical and release locked
            deductAndUnlockWithRetry(o.getWarehouseId(), it.getProductId(), shipQty, shipNo, now);

            it.setShippedQty(safeQty(it.getShippedQty()).add(shipQty));
            detailRepository.save(it);

            SalShipDetail sd = new SalShipDetail();
            sd.setShipId(ship.getId());
            sd.setOrderId(o.getId());
            sd.setOrderDetailId(it.getId());
            sd.setProductId(it.getProductId());
            sd.setProductCode(it.getProductCode());
            sd.setProductName(it.getProductName());
            sd.setUnit(it.getUnit());
            sd.setQty(shipQty);
            shipDetails.add(sd);
            totalQty = totalQty.add(shipQty);
        }

        if (totalQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "total ship qty must be > 0");
        }

        ship.setTotalQty(totalQty);
        shipRepository.saveAndFlush(ship);
        shipDetailRepository.saveAll(shipDetails);

        // Create WMS bill for this shipment (idempotent by ship.id + type)
        WmsIoBill bill = ioBillRepository.findFirstByBizIdAndType(ship.getId(), WMS_BILL_TYPE_SALES_OUT).orElse(null);
        if (bill == null) {
            bill = new WmsIoBill();
            bill.setBillNo(generateWmsBillNo());
            bill.setType(WMS_BILL_TYPE_SALES_OUT);
            bill.setBizId(ship.getId());
            bill.setBizNo(shipNo);
            bill.setWarehouseId(o.getWarehouseId());
            bill.setStatus(WMS_BILL_STATUS_COMPLETED);
            bill.setRemark("销售出库");
            bill.setCreateBy(trimToNull(operator));
            bill.setCreateTime(now);
            try {
                bill = ioBillRepository.saveAndFlush(bill);
            } catch (DataIntegrityViolationException e) {
                bill = ioBillRepository.findFirstByBizIdAndType(ship.getId(), WMS_BILL_TYPE_SALES_OUT).orElseThrow(() -> e);
            }

            List<WmsIoBillDetail> billDetails = new ArrayList<>();
            for (SalShipDetail sd : shipDetails) {
                WmsIoBillDetail bd = new WmsIoBillDetail();
                bd.setBillId(bill.getId());
                bd.setProductId(sd.getProductId());
                bd.setQty(sd.getQty());
                bd.setRealQty(sd.getQty());
                billDetails.add(bd);
            }
            ioBillDetailRepository.saveAll(billDetails);
        }

        ship.setWmsBillId(bill.getId());
        ship.setWmsBillNo(bill.getBillNo());
        shipRepository.saveAndFlush(ship);

        // Update order status
        o.setWmsBillId(bill.getId());
        o.setWmsBillNo(bill.getBillNo());
        boolean allShipped = items.stream().allMatch(it -> safeQty(it.getQty()).compareTo(safeQty(it.getShippedQty())) <= 0);
        o.setStatus(allShipped ? STATUS_SHIPPED : STATUS_PARTIAL_SHIPPED);
        if (allShipped) {
            o.setShipBy(trimToNull(operator));
            o.setShipTime(now);
        }
        orderRepository.saveAndFlush(o);
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

    private static SalOrderResponse toResponse(SalOrderRepository.SalOrderRow r) {
        if (r == null) return null;
        return new SalOrderResponse(
                r.getId(),
                r.getOrderNo(),
                r.getCustomerId(),
                r.getCustomerCode(),
                r.getCustomerName(),
                r.getWarehouseId(),
                r.getWarehouseName(),
                r.getOrderDate(),
                r.getTotalAmount(),
                r.getStatus(),
                r.getRemark(),
                r.getWmsBillId(),
                r.getWmsBillNo(),
                r.getCreateBy(),
                r.getCreateTime(),
                r.getAuditBy(),
                r.getAuditTime(),
                r.getShipBy(),
                r.getShipTime());
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

    private static BigDecimal safeQty(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String generateOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "SO" + ts + "-" + rand;
    }

    private static String generateShipNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "SHIP" + ts + "-" + rand;
    }

    private static String generateWmsBillNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "SAO" + ts + "-" + rand;
    }

    public record ShipLine(Long orderDetailId, Long productId, BigDecimal qty) {
    }
}
