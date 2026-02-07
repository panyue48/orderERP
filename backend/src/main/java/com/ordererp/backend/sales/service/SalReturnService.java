package com.ordererp.backend.sales.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.sales.dto.SalReturnCreateRequest;
import com.ordererp.backend.sales.dto.SalReturnDetailResponse;
import com.ordererp.backend.sales.dto.SalReturnExecuteResponse;
import com.ordererp.backend.sales.dto.SalReturnItemResponse;
import com.ordererp.backend.sales.dto.SalReturnLineRequest;
import com.ordererp.backend.sales.dto.SalReturnResponse;
import com.ordererp.backend.sales.entity.SalReturn;
import com.ordererp.backend.sales.entity.SalReturnDetail;
import com.ordererp.backend.sales.entity.SalShip;
import com.ordererp.backend.sales.entity.SalShipDetail;
import com.ordererp.backend.sales.repository.SalOrderDetailRepository;
import com.ordererp.backend.sales.repository.SalReturnDetailRepository;
import com.ordererp.backend.sales.repository.SalReturnRepository;
import com.ordererp.backend.sales.repository.SalShipDetailRepository;
import com.ordererp.backend.sales.repository.SalShipRepository;
import com.ordererp.backend.wms.entity.WmsStockQc;
import com.ordererp.backend.wms.entity.WmsIoBill;
import com.ordererp.backend.wms.entity.WmsIoBillDetail;
import com.ordererp.backend.wms.entity.WmsStock;
import com.ordererp.backend.wms.entity.WmsStockLog;
import com.ordererp.backend.wms.repository.WmsIoBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsIoBillRepository;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import com.ordererp.backend.wms.repository.WmsStockQcRepository;
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
    private static final int STATUS_PENDING_QC = 3;
    private static final int STATUS_COMPLETED = 4;
    private static final int STATUS_QC_REJECTED = 5;
    private static final int STATUS_CANCELED = 9;

    private static final int PARTNER_TYPE_CUSTOMER = 2;

    private static final int WMS_BILL_TYPE_SALES_RETURN_IN = 6;
    private static final int WMS_BILL_STATUS_COMPLETED = 2;

    private final SalReturnRepository returnRepository;
    private final SalReturnDetailRepository detailRepository;
    private final SalShipRepository shipRepository;
    private final SalShipDetailRepository shipDetailRepository;
    private final SalOrderDetailRepository orderDetailRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockQcRepository stockQcRepository;
    private final WmsStockLogRepository stockLogRepository;

    public SalReturnService(SalReturnRepository returnRepository, SalReturnDetailRepository detailRepository,
            SalShipRepository shipRepository, SalShipDetailRepository shipDetailRepository, SalOrderDetailRepository orderDetailRepository,
            BasePartnerRepository partnerRepository, BaseWarehouseRepository warehouseRepository,
            WmsIoBillRepository ioBillRepository,
            WmsIoBillDetailRepository ioBillDetailRepository, WmsStockRepository stockRepository,
            WmsStockQcRepository stockQcRepository,
            WmsStockLogRepository stockLogRepository) {
        this.returnRepository = returnRepository;
        this.detailRepository = detailRepository;
        this.shipRepository = shipRepository;
        this.shipDetailRepository = shipDetailRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.partnerRepository = partnerRepository;
        this.warehouseRepository = warehouseRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockRepository = stockRepository;
        this.stockQcRepository = stockQcRepository;
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
                        r.getShipDetailId(),
                        r.getOrderDetailId(),
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
        if (request.shipId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipId is required");

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

        SalShip ship = shipRepository.findById(request.shipId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "发货批次不存在"));
        if (ship.getReverseStatus() != null && ship.getReverseStatus() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该发货批次已冲销，不允许退货");
        }
        if (!Objects.equals(ship.getCustomerId(), customer.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货客户与发货批次不匹配");
        }
        if (!Objects.equals(ship.getWarehouseId(), wh.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货仓库与发货批次仓库不一致");
        }

        validateLines(request.lines());

        List<SalShipDetail> shipDetails = shipDetailRepository.findByShipIdOrderByIdAsc(ship.getId());
        if (shipDetails == null || shipDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发货批次无明细，无法退货");
        }

        var orderDetails = orderDetailRepository.findByOrderIdOrderByIdAsc(ship.getOrderId());
        java.util.Map<Long, com.ordererp.backend.sales.entity.SalOrderDetail> orderDetailById = new java.util.HashMap<>();
        if (orderDetails != null) {
            for (var od : orderDetails) {
                if (od != null && od.getId() != null) orderDetailById.put(od.getId(), od);
            }
        }

        java.util.Map<Long, SalShipDetail> shipDetailById = new java.util.HashMap<>();
        for (var sd : shipDetails) {
            if (sd != null && sd.getId() != null) shipDetailById.put(sd.getId(), sd);
        }

        List<Long> shipDetailIds = request.lines().stream().map(SalReturnLineRequest::shipDetailId).filter(Objects::nonNull).toList();
        java.util.Map<Long, BigDecimal> returnedByShipDetailId = new java.util.HashMap<>();
        for (var rr : detailRepository.sumReturnedQtyByShipDetailIds(shipDetailIds)) {
            if (rr == null || rr.getShipDetailId() == null) continue;
            returnedByShipDetailId.put(rr.getShipDetailId(), safeQty(rr.getReturnedQty()));
        }

        LocalDateTime now = LocalDateTime.now();
        SalReturn header = new SalReturn();
        header.setReturnNo(generateReturnNo());
        header.setCustomerId(customer.getId());
        header.setCustomerCode(customer.getPartnerCode());
        header.setCustomerName(customer.getPartnerName());
        header.setWarehouseId(wh.getId());
        header.setShipId(ship.getId());
        header.setShipNo(ship.getShipNo());
        header.setOrderId(ship.getOrderId());
        header.setOrderNo(ship.getOrderNo());
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
            SalShipDetail sd = shipDetailById.get(line.shipDetailId());
            if (sd == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipDetailId invalid: " + line.shipDetailId());
            }

            BigDecimal qty = safeQty(line.qty());

            BigDecimal shippedQty = safeQty(sd.getQty());
            BigDecimal returnedQty = safeQty(returnedByShipDetailId.get(sd.getId()));
            BigDecimal returnable = shippedQty.subtract(returnedQty);
            if (returnable.compareTo(BigDecimal.ZERO) < 0) returnable = BigDecimal.ZERO;
            if (qty.compareTo(returnable) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货数量超出可退数量");
            }

            var od = orderDetailById.get(sd.getOrderDetailId());
            BigDecimal price = safeMoney(od == null ? null : od.getPrice());
            BigDecimal amount = price.multiply(qty);

            SalReturnDetail d = new SalReturnDetail();
            d.setReturnId(header.getId());
            d.setShipDetailId(sd.getId());
            d.setOrderDetailId(sd.getOrderDetailId());
            d.setProductId(sd.getProductId());
            d.setProductCode(sd.getProductCode());
            d.setProductName(sd.getProductName());
            d.setUnit(sd.getUnit());
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
        if (Objects.equals(r.getStatus(), STATUS_QC_REJECTED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已质检不合格");
        }
        if (Objects.equals(r.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已完成");
        }
        if (!Objects.equals(r.getStatus(), STATUS_PENDING_AUDIT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单状态不允许审核");
        }

        validateAgainstShipmentOrThrow(r);

        r.setStatus(STATUS_AUDITED);
        r.setAuditBy(trimToNull(auditBy));
        r.setAuditTime(LocalDateTime.now());
        returnRepository.save(r);
        return toResponse(returnRepository.getRow(r.getId()));
    }

    /**
     * 收货入待检：将退货数量计入 wms_stock_qc（待检库存），不增加可用库存。
     *
     * <p>幂等语义：</p>
     * <ul>
     *   <li>同一退货单重复 receive 不会重复加待检库存：通过状态机（仅 2->3）保证。</li>
     * </ul>
     */
    @Transactional
    public SalReturnResponse receive(Long id, String operator) {
        SalReturn r = returnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在"));
        if (Objects.equals(r.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已作废");
        }
        if (Objects.equals(r.getStatus(), STATUS_QC_REJECTED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已质检不合格");
        }
        if (Objects.equals(r.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已完成");
        }
        if (Objects.equals(r.getStatus(), STATUS_PENDING_QC)) {
            return toResponse(returnRepository.getRow(r.getId()));
        }
        if (!Objects.equals(r.getStatus(), STATUS_AUDITED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单未审核，不能收货");
        }

        validateAgainstShipmentOrThrow(r);

        BaseWarehouse wh = warehouseRepository.findByIdAndDeleted(r.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库不存在"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仓库已禁用");
        }

        List<SalReturnDetail> details = detailRepository.findByReturnIdOrderByIdAsc(r.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单没有明细");
        }

        // aggregate by product for QC bucket update
        java.util.Map<Long, BigDecimal> qcDeltaByProductId = new java.util.HashMap<>();
        for (SalReturnDetail d : details) {
            if (d == null) continue;
            BigDecimal qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            qcDeltaByProductId.merge(d.getProductId(), qty, BigDecimal::add);
        }
        for (var e : qcDeltaByProductId.entrySet()) {
            BigDecimal qty = safeQty(e.getValue());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            increaseQcWithRetry(r.getWarehouseId(), e.getKey(), qty);
        }

        LocalDateTime now = LocalDateTime.now();
        r.setStatus(STATUS_PENDING_QC);
        r.setReceiveBy(trimToNull(operator));
        r.setReceiveTime(now);
        returnRepository.save(r);
        return toResponse(returnRepository.getRow(r.getId()));
    }

    /**
     * 质检通过并入库：从“待检库存”扣减并增加可用库存，同时生成 WMS 入库单（type=6）。
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
        if (Objects.equals(r.getStatus(), STATUS_QC_REJECTED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已质检不合格，不能入库");
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

        if (!Objects.equals(r.getStatus(), STATUS_PENDING_QC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单未收货入待检，不能质检入库");
        }

        validateAgainstShipmentOrThrow(r);

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

            // 待检库存 -> 可用库存（质检通过后才进入可用库存）
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
        r.setQcBy(trimToNull(operator));
        r.setQcTime(now);
        r.setExecuteBy(trimToNull(operator));
        r.setExecuteTime(now);
        returnRepository.save(r);
        return new SalReturnExecuteResponse(r.getId(), r.getReturnNo(), r.getStatus(), bill.getId(), bill.getBillNo());
    }

    @Transactional
    public SalReturnResponse qcReject(Long id, String operator, String disposition, String qcRemark) {
        SalReturn r = returnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "退货单不存在"));
        if (Objects.equals(r.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已作废");
        }
        if (Objects.equals(r.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单已完成，不能质检不合格");
        }
        if (!Objects.equals(r.getStatus(), STATUS_PENDING_QC)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单未收货入待检，不能质检不合格");
        }

        validateAgainstShipmentOrThrow(r);

        List<SalReturnDetail> details = detailRepository.findByReturnIdOrderByIdAsc(r.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单没有明细");
        }

        java.util.Map<Long, BigDecimal> qcDeltaByProductId = new java.util.HashMap<>();
        for (SalReturnDetail d : details) {
            if (d == null) continue;
            BigDecimal qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            qcDeltaByProductId.merge(d.getProductId(), qty, BigDecimal::add);
        }
        for (var e : qcDeltaByProductId.entrySet()) {
            BigDecimal qty = safeQty(e.getValue());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            decreaseQcWithRetry(r.getWarehouseId(), e.getKey(), qty);
        }

        LocalDateTime now = LocalDateTime.now();
        r.setStatus(STATUS_QC_REJECTED);
        r.setQcBy(trimToNull(operator));
        r.setQcTime(now);
        r.setQcDisposition(trimToNull(disposition));
        r.setQcRemark(trimToNull(qcRemark));
        returnRepository.save(r);
        return toResponse(returnRepository.getRow(r.getId()));
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
        if (Objects.equals(r.getStatus(), STATUS_PENDING_QC)) {
            List<SalReturnDetail> details = detailRepository.findByReturnIdOrderByIdAsc(r.getId());
            java.util.Map<Long, BigDecimal> qcDeltaByProductId = new java.util.HashMap<>();
            for (SalReturnDetail d : details) {
                if (d == null) continue;
                BigDecimal qty = safeQty(d.getQty());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                qcDeltaByProductId.merge(d.getProductId(), qty, BigDecimal::add);
            }
            for (var e : qcDeltaByProductId.entrySet()) {
                BigDecimal qty = safeQty(e.getValue());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                decreaseQcWithRetry(r.getWarehouseId(), e.getKey(), qty);
            }
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
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be positive");
        }
        LocalDateTime now = LocalDateTime.now();
        WmsStockQc qc = stockQcRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElse(null);
        if (qc == null) {
            WmsStockQc created = new WmsStockQc();
            created.setWarehouseId(warehouseId);
            created.setProductId(productId);
            created.setQcQty(qty);
            created.setVersion(0);
            created.setUpdateTime(now);
            try {
                return stockQcRepository.saveAndFlush(created);
            } catch (DataIntegrityViolationException e) {
                qc = stockQcRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElseThrow(() -> e);
            }
        }
        BigDecimal qcQty = safeQty(qc.getQcQty());
        qc.setQcQty(qcQty.add(qty));
        qc.setUpdateTime(now);
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
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be positive");
        }
        LocalDateTime now = LocalDateTime.now();
        WmsStockQc qc = stockQcRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElse(null);
        if (qc == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "QC stock bucket missing (warehouseId=" + warehouseId + ", productId=" + productId + ")");
        }
        BigDecimal qcQty = safeQty(qc.getQcQty());
        BigDecimal next = qcQty.subtract(qty);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "QC qty insufficient (warehouseId=" + warehouseId + ", productId=" + productId + ", qcQty=" + qcQty + ", required=" + qty + ")");
        }
        qc.setQcQty(next);
        qc.setUpdateTime(now);
        return stockQcRepository.saveAndFlush(qc);
    }

    private static void validateLines(List<SalReturnLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }
        Set<Long> seen = new HashSet<>();
        for (SalReturnLineRequest line : lines) {
            if (line == null || line.shipDetailId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipDetailId is required");
            }
            if (!seen.add(line.shipDetailId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate shipDetailId: " + line.shipDetailId());
            }
            BigDecimal qty = safeQty(line.qty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be positive");
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
                row.getShipId(),
                row.getShipNo(),
                row.getOrderId(),
                row.getOrderNo(),
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
                row.getReceiveBy(),
                row.getReceiveTime(),
                row.getQcBy(),
                row.getQcTime(),
                row.getQcDisposition(),
                row.getQcRemark(),
                row.getExecuteBy(),
                row.getExecuteTime());
    }

    private void validateAgainstShipmentOrThrow(SalReturn r) {
        if (r == null) return;
        if (r.getShipId() == null) return;

        SalShip ship = shipRepository.findById(r.getShipId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "发货批次不存在"));
        if (ship.getReverseStatus() != null && ship.getReverseStatus() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该发货批次已冲销，不允许退货");
        }
        if (!Objects.equals(ship.getCustomerId(), r.getCustomerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货客户与发货批次不匹配");
        }
        if (!Objects.equals(ship.getWarehouseId(), r.getWarehouseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货仓库与发货批次仓库不一致");
        }

        List<SalShipDetail> shipDetails = shipDetailRepository.findByShipIdOrderByIdAsc(ship.getId());
        if (shipDetails == null || shipDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发货批次无明细，无法校验退货");
        }
        List<Long> shipDetailIds = shipDetails.stream().map(SalShipDetail::getId).filter(Objects::nonNull).toList();
        java.util.Set<Long> shipDetailIdSet = new java.util.HashSet<>(shipDetailIds);

        List<SalReturnDetail> returnDetails = detailRepository.findByReturnIdOrderByIdAsc(r.getId());
        if (returnDetails == null || returnDetails.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货单没有明细");
        }
        for (SalReturnDetail d : returnDetails) {
            if (d == null) continue;
            if (d.getShipDetailId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货明细缺少来源发货批次行，无法审核/执行（请作废后重建）");
            }
            if (!shipDetailIdSet.contains(d.getShipDetailId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货明细来源发货批次不匹配，无法审核/执行（请作废后重建）");
            }
        }

        java.util.Map<Long, BigDecimal> returnedById = new java.util.HashMap<>();
        for (var rr : detailRepository.sumReturnedQtyByShipDetailIds(shipDetailIds)) {
            if (rr == null || rr.getShipDetailId() == null) continue;
            returnedById.put(rr.getShipDetailId(), safeQty(rr.getReturnedQty()));
        }

        for (SalShipDetail sd : shipDetails) {
            if (sd == null || sd.getId() == null) continue;
            BigDecimal shipped = safeQty(sd.getQty());
            BigDecimal returned = safeQty(returnedById.get(sd.getId()));
            if (returned.compareTo(shipped) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退货数量已超出发货数量");
            }
        }
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
