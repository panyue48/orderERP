package com.ordererp.backend.wms.service;

import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.wms.dto.StockOutBillCreateRequest;
import com.ordererp.backend.wms.dto.StockOutBillDetailResponse;
import com.ordererp.backend.wms.dto.StockOutBillItemResponse;
import com.ordererp.backend.wms.dto.StockOutBillLineRequest;
import com.ordererp.backend.wms.dto.StockOutBillResponse;
import com.ordererp.backend.wms.dto.WmsBillPrecheckLine;
import com.ordererp.backend.wms.dto.WmsBillPrecheckResponse;
import com.ordererp.backend.wms.dto.WmsReverseResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WmsStockOutBillService {
    private static final int BILL_TYPE_STOCK_OUT = 4;
    private static final int BILL_TYPE_STOCK_IN = 3;
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_COMPLETED = 2;

    private final WmsIoBillRepository billRepository;
    private final WmsIoBillDetailRepository billDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockLogRepository stockLogRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;

    public WmsStockOutBillService(WmsIoBillRepository billRepository, WmsIoBillDetailRepository billDetailRepository,
            WmsStockRepository stockRepository, WmsStockLogRepository stockLogRepository,
            BaseWarehouseRepository warehouseRepository, BaseProductRepository productRepository) {
        this.billRepository = billRepository;
        this.billDetailRepository = billDetailRepository;
        this.stockRepository = stockRepository;
        this.stockLogRepository = stockLogRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
    }

    public Page<StockOutBillResponse> page(String keyword, Pageable pageable) {
        return billRepository.pageBillRows(BILL_TYPE_STOCK_OUT, keyword, pageable)
                .map(r -> new StockOutBillResponse(
                        r.getId(),
                        r.getBillNo(),
                        r.getWarehouseId(),
                        r.getWarehouseName(),
                        r.getStatus(),
                        r.getTotalQty(),
                        r.getRemark(),
                        r.getCreateBy(),
                        r.getCreateTime()));
    }

    public StockOutBillDetailResponse detail(Long id) {
        WmsIoBillRepository.BillRow bill = billRepository.getBillRow(id);
        if (bill == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "bill not found");
        }
        List<StockOutBillItemResponse> items = billDetailRepository.listBillItemRows(id).stream()
                .map(r -> new StockOutBillItemResponse(
                        r.getId(),
                        r.getProductId(),
                        r.getProductCode(),
                        r.getProductName(),
                        r.getUnit(),
                        r.getQty(),
                        r.getRealQty()))
                .toList();
        return new StockOutBillDetailResponse(
                bill.getId(),
                bill.getBillNo(),
                bill.getWarehouseId(),
                bill.getWarehouseName(),
                bill.getStatus(),
                bill.getTotalQty(),
                bill.getRemark(),
                bill.getCreateBy(),
                bill.getCreateTime(),
                items);
    }

    public WmsBillPrecheckResponse precheckExecute(Long id) {
        WmsIoBill bill = billRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "bill not found"));
        if (!Objects.equals(bill.getType(), BILL_TYPE_STOCK_OUT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill type mismatch");
        }
        if (Objects.equals(bill.getStatus(), STATUS_COMPLETED)) {
            return new WmsBillPrecheckResponse(true, "already completed", List.of());
        }

        var wh = warehouseRepository.findByIdAndDeleted(bill.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse not found"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            return new WmsBillPrecheckResponse(false, "warehouse disabled", List.of());
        }

        List<WmsIoBillDetail> details = billDetailRepository.findByBillId(bill.getId());
        if (details.isEmpty()) {
            return new WmsBillPrecheckResponse(false, "bill has no details", List.of());
        }

        List<WmsBillPrecheckLine> lines = new ArrayList<>();
        boolean ok = true;
        String message = "ok";
        Set<Long> seen = new HashSet<>();

        var itemRows = billDetailRepository.listBillItemRows(bill.getId());
        for (WmsIoBillDetail d : details) {
            boolean lineOk = true;
            String lineMsg = "ok";
            if (d.getProductId() == null || !seen.add(d.getProductId())) {
                lineOk = false;
                lineMsg = "duplicate product";
            }
            if (d.getRealQty() != null && d.getRealQty().compareTo(BigDecimal.ZERO) > 0) {
                lineOk = false;
                lineMsg = "already executed";
            }
            BigDecimal qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                lineOk = false;
                lineMsg = "qty must be positive";
            }

            var item = itemRows.stream().filter(r -> Objects.equals(r.getId(), d.getId())).findFirst().orElse(null);
            String productCode = item != null ? item.getProductCode() : null;
            String productName = item != null ? item.getProductName() : null;
            String unit = item != null ? item.getUnit() : null;

            BigDecimal stockQty = BigDecimal.ZERO;
            BigDecimal lockedQty = BigDecimal.ZERO;
            BigDecimal available = BigDecimal.ZERO;

            if (d.getProductId() != null) {
                var p = productRepository.findByIdAndDeleted(d.getProductId(), 0).orElse(null);
                if (p == null) {
                    lineOk = false;
                    lineMsg = "product not found";
                } else if (p.getStatus() != null && p.getStatus() != 1) {
                    lineOk = false;
                    lineMsg = "product disabled";
                } else {
                    if (productCode == null) productCode = p.getProductCode();
                    if (productName == null) productName = p.getProductName();
                    if (unit == null) unit = p.getUnit();
                }

                WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(bill.getWarehouseId(), d.getProductId())
                        .orElse(null);
                if (stock == null) {
                    lineOk = false;
                    lineMsg = "stock not found";
                } else {
                    stockQty = safeQty(stock.getStockQty());
                    lockedQty = safeQty(stock.getLockedQty());
                    if (lockedQty.compareTo(stockQty) > 0) {
                        lineOk = false;
                        lineMsg = "invalid lockedQty > stockQty";
                    }
                    available = stockQty.subtract(lockedQty);
                    if (available.compareTo(qty) < 0) {
                        lineOk = false;
                        lineMsg = "insufficient stock";
                    }
                }
            }

            if (!lineOk) ok = false;
            lines.add(new WmsBillPrecheckLine(d.getProductId(), productCode, productName, unit, qty, stockQty, lockedQty, available, lineOk, lineMsg));
        }

        if (!ok) message = "precheck failed";
        return new WmsBillPrecheckResponse(ok, message, lines);
    }

    /**
     * 出库单冲销（反冲）。
     *
     * <p>第三阶段关键实现点（工程化与健壮性）：</p>
     * <ul>
     *   <li><b>幂等</b>：重复调用 reverse 必须返回同一张冲销单，不能重复加库存/重复写流水。</li>
     *   <li><b>并发</b>：对原始单据加悲观锁（for update），并依赖数据库唯一约束 (biz_id, type) 只生成一张冲销单；
     *       若并发撞唯一键，则读取并返回已存在的冲销单作为兜底。</li>
     * </ul>
     */
    @Transactional
    public WmsReverseResponse reverse(Long id, String createdBy) {
        WmsIoBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "bill not found"));
        if (!Objects.equals(bill.getType(), BILL_TYPE_STOCK_OUT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill type mismatch");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill not completed");
        }
        if (bill.getBizId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot reverse a reversal bill");
        }

        WmsIoBill existing = billRepository.findFirstByBizIdAndType(bill.getId(), BILL_TYPE_STOCK_IN).orElse(null);
        if (existing != null) {
            return new WmsReverseResponse(existing.getId(), existing.getBillNo());
        }

        List<WmsIoBillDetail> details = billDetailRepository.findByBillId(bill.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill has no details");
        }

        WmsIoBill reversal = new WmsIoBill();
        reversal.setBillNo(generateBillNo("RSO"));
        reversal.setType(BILL_TYPE_STOCK_IN);
        reversal.setBizId(bill.getId());
        reversal.setBizNo(bill.getBillNo());
        reversal.setWarehouseId(bill.getWarehouseId());
        reversal.setStatus(STATUS_COMPLETED);
        reversal.setRemark("冲销盘点出库: " + bill.getBillNo());
        reversal.setCreateBy(trimToNull(createdBy));
        reversal.setCreateTime(LocalDateTime.now());
        try {
            reversal = billRepository.saveAndFlush(reversal);
        } catch (DataIntegrityViolationException e) {
            // 并发下命中 Unique(biz_id, type)：说明已有冲销单，按幂等语义返回 existing。
            existing = billRepository.findFirstByBizIdAndType(bill.getId(), BILL_TYPE_STOCK_IN).orElse(null);
            if (existing != null) {
                return new WmsReverseResponse(existing.getId(), existing.getBillNo());
            }
            throw e;
        }

        for (WmsIoBillDetail d : details) {
            BigDecimal qty = safeQty(d.getRealQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid executed qty on detail: " + d.getId());
            }

            WmsIoBillDetail rd = new WmsIoBillDetail();
            rd.setBillId(reversal.getId());
            rd.setProductId(d.getProductId());
            rd.setQty(qty);
            rd.setRealQty(qty);
            billDetailRepository.save(rd);

            WmsStock stock = increaseStockWithRetry(bill.getWarehouseId(), d.getProductId(), qty);
            WmsStockLog log = new WmsStockLog();
            log.setWarehouseId(bill.getWarehouseId());
            log.setProductId(d.getProductId());
            log.setBizType("REVERSAL_OUT");
            log.setBizNo(reversal.getBillNo());
            log.setChangeQty(qty);
            log.setAfterStockQty(safeQty(stock.getStockQty()));
            log.setCreateTime(LocalDateTime.now());
            stockLogRepository.save(log);
        }

        return new WmsReverseResponse(reversal.getId(), reversal.getBillNo());
    }

    @Transactional
    public StockOutBillResponse create(StockOutBillCreateRequest request, String createdBy) {
        var wh = warehouseRepository.findByIdAndDeleted(request.warehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse not found"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse disabled");
        }

        validateLines(request.lines());

        WmsIoBill bill = new WmsIoBill();
        bill.setBillNo(generateBillNo());
        bill.setType(BILL_TYPE_STOCK_OUT);
        bill.setWarehouseId(request.warehouseId());
        bill.setStatus(STATUS_PENDING);
        bill.setRemark(trimToNull(request.remark()));
        bill.setCreateBy(trimToNull(createdBy));
        bill.setCreateTime(LocalDateTime.now());
        bill = billRepository.save(bill);

        for (StockOutBillLineRequest line : request.lines()) {
            var p = productRepository.findByIdAndDeleted(line.productId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "product not found: " + line.productId()));
            if (p.getStatus() != null && p.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product disabled: " + line.productId());
            }
            WmsIoBillDetail d = new WmsIoBillDetail();
            d.setBillId(bill.getId());
            d.setProductId(line.productId());
            d.setQty(line.qty());
            d.setRealQty(BigDecimal.ZERO);
            billDetailRepository.save(d);
        }

        WmsIoBillRepository.BillRow row = billRepository.getBillRow(bill.getId());
        return new StockOutBillResponse(
                row.getId(),
                row.getBillNo(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getStatus(),
                row.getTotalQty(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime());
    }

    /**
     * 执行出库单。
     *
     * <p>第三阶段关键实现点（工程化与健壮性）：</p>
     * <ul>
     *   <li><b>幂等</b>：已完成的单据再次执行，直接返回“已完成”结果，不重复扣库存/不重复写流水。</li>
     *   <li><b>并发</b>：对单据加悲观锁（for update），避免同一张单被并发执行导致重复出库。</li>
     *   <li><b>一致性</b>：执行过程中持续校验库存不变量（stock_qty/locked_qty），发现异常立即中断。</li>
     * </ul>
     */
    @Transactional
    public StockOutBillResponse execute(Long id) {
        WmsIoBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "bill not found"));
        if (!Objects.equals(bill.getType(), BILL_TYPE_STOCK_OUT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill type mismatch");
        }
        if (Objects.equals(bill.getStatus(), STATUS_COMPLETED)) {
            WmsIoBillRepository.BillRow row = billRepository.getBillRow(bill.getId());
            return new StockOutBillResponse(
                    row.getId(),
                    row.getBillNo(),
                    row.getWarehouseId(),
                    row.getWarehouseName(),
                    row.getStatus(),
                    row.getTotalQty(),
                    row.getRemark(),
                    row.getCreateBy(),
                    row.getCreateTime());
        }

        var wh = warehouseRepository.findByIdAndDeleted(bill.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse not found"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse disabled");
        }

        List<WmsIoBillDetail> details = billDetailRepository.findByBillId(bill.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill has no details");
        }

        // 先校验所有明细，再做任何库存变更（避免“部分成功、部分失败”）。
        Set<Long> seen = new HashSet<>();
        for (WmsIoBillDetail d : details) {
            if (!seen.add(d.getProductId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate productId on bill: " + d.getProductId());
            }
            if (d.getRealQty() != null && d.getRealQty().compareTo(BigDecimal.ZERO) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bill detail already executed: " + d.getId());
            }
            BigDecimal qty = safeQty(d.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid qty on bill detail: " + d.getId());
            }
            var p = productRepository.findByIdAndDeleted(d.getProductId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "product not found: " + d.getProductId()));
            if (p.getStatus() != null && p.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product disabled: " + d.getProductId());
            }
            WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(bill.getWarehouseId(), d.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "stock not found (warehouseId=" + bill.getWarehouseId() + ", productId=" + d.getProductId()
                                    + ")"));
            BigDecimal stockQty = safeQty(stock.getStockQty());
            BigDecimal lockedQty = safeQty(stock.getLockedQty());
            if (lockedQty.compareTo(BigDecimal.ZERO) < 0 || stockQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid stock state");
            }
            if (lockedQty.compareTo(stockQty) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid lockedQty > stockQty");
            }
            BigDecimal available = stockQty.subtract(lockedQty);
            if (available.compareTo(qty) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "insufficient stock (productId=" + d.getProductId() + ", available=" + available + ", required=" + qty + ")");
            }
        }

        for (WmsIoBillDetail d : details) {
            BigDecimal qty = safeQty(d.getQty());
            WmsStock stock = deductStockWithRetry(bill.getWarehouseId(), d.getProductId(), qty);
            LocalDateTime now = LocalDateTime.now();

            d.setRealQty(qty);
            billDetailRepository.save(d);

            WmsStockLog log = new WmsStockLog();
            log.setWarehouseId(bill.getWarehouseId());
            log.setProductId(d.getProductId());
            log.setBizType("STOCK_OUT");
            log.setBizNo(bill.getBillNo());
            log.setChangeQty(qty.negate());
            log.setAfterStockQty(safeQty(stock.getStockQty()));
            log.setCreateTime(now);
            stockLogRepository.save(log);
        }

        bill.setStatus(STATUS_COMPLETED);
        billRepository.save(bill);

        WmsIoBillRepository.BillRow row = billRepository.getBillRow(bill.getId());
        return new StockOutBillResponse(
                row.getId(),
                row.getBillNo(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getStatus(),
                row.getTotalQty(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime());
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "stock not found (warehouseId=" + warehouseId + ", productId=" + productId + ")"));

        BigDecimal stockQty = safeQty(stock.getStockQty());
        BigDecimal lockedQty = safeQty(stock.getLockedQty());
        validateStockInvariant(stockQty, lockedQty);
        BigDecimal available = stockQty.subtract(lockedQty);
        if (available.compareTo(qty) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "insufficient stock (available=" + available + ", required=" + qty + ")");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal nextStockQty = stockQty.subtract(qty);
        stock.setStockQty(nextStockQty);
        stock.setLockedQty(lockedQty);
        stock.setUpdateTime(now);
        validateStockInvariant(stock.getStockQty(), stock.getLockedQty());
        return stockRepository.saveAndFlush(stock);
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
            stock = new WmsStock();
            stock.setWarehouseId(warehouseId);
            stock.setProductId(productId);
            stock.setStockQty(qty);
            stock.setLockedQty(BigDecimal.ZERO);
            stock.setVersion(0);
            stock.setUpdateTime(now);
            validateStockInvariant(stock.getStockQty(), stock.getLockedQty());
            return stockRepository.saveAndFlush(stock);
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

    private static void validateLines(List<StockOutBillLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (StockOutBillLineRequest line : lines) {
            if (line.productId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
            }
            if (line.qty() == null || line.qty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be positive");
            }
            if (!seen.add(line.productId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate productId: " + line.productId());
            }
        }
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

    private static String generateBillNo() {
        return generateBillNo("SO");
    }

    private static String generateBillNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return prefix + ts + "-" + rand;
    }
}
