package com.ordererp.backend.wms.service;

import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.wms.dto.WmsCheckBillCreateRequest;
import com.ordererp.backend.wms.dto.WmsCheckBillDetailResponse;
import com.ordererp.backend.wms.dto.WmsCheckBillItemResponse;
import com.ordererp.backend.wms.dto.WmsCheckBillLineRequest;
import com.ordererp.backend.wms.dto.WmsCheckBillResponse;
import com.ordererp.backend.wms.dto.WmsCheckExecuteResponse;
import com.ordererp.backend.wms.entity.WmsCheckBill;
import com.ordererp.backend.wms.entity.WmsCheckBillDetail;
import com.ordererp.backend.wms.entity.WmsIoBill;
import com.ordererp.backend.wms.entity.WmsIoBillDetail;
import com.ordererp.backend.wms.entity.WmsStock;
import com.ordererp.backend.wms.entity.WmsStockLog;
import com.ordererp.backend.wms.repository.WmsCheckBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsCheckBillRepository;
import com.ordererp.backend.wms.repository.WmsIoBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsIoBillRepository;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import com.ordererp.backend.wms.repository.WmsStockRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
public class WmsCheckBillService {
    private static final int BILL_TYPE_STOCK_IN = 3;
    private static final int BILL_TYPE_STOCK_OUT = 4;
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_COMPLETED = 2;

    private final WmsCheckBillRepository checkBillRepository;
    private final WmsCheckBillDetailRepository checkBillDetailRepository;
    private final WmsIoBillRepository ioBillRepository;
    private final WmsIoBillDetailRepository ioBillDetailRepository;
    private final WmsStockRepository stockRepository;
    private final WmsStockLogRepository stockLogRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BaseProductRepository productRepository;

    public WmsCheckBillService(WmsCheckBillRepository checkBillRepository,
            WmsCheckBillDetailRepository checkBillDetailRepository,
            WmsIoBillRepository ioBillRepository,
            WmsIoBillDetailRepository ioBillDetailRepository,
            WmsStockRepository stockRepository,
            WmsStockLogRepository stockLogRepository,
            BaseWarehouseRepository warehouseRepository,
            BaseProductRepository productRepository) {
        this.checkBillRepository = checkBillRepository;
        this.checkBillDetailRepository = checkBillDetailRepository;
        this.ioBillRepository = ioBillRepository;
        this.ioBillDetailRepository = ioBillDetailRepository;
        this.stockRepository = stockRepository;
        this.stockLogRepository = stockLogRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
    }

    public Page<WmsCheckBillResponse> page(String keyword, Pageable pageable) {
        return checkBillRepository.pageRows(keyword, pageable).map(r -> new WmsCheckBillResponse(
                r.getId(),
                r.getBillNo(),
                r.getWarehouseId(),
                r.getWarehouseName(),
                r.getStatus(),
                r.getRemark(),
                r.getCreateBy(),
                r.getCreateTime(),
                r.getExecuteTime()));
    }

    public WmsCheckBillDetailResponse detail(Long id) {
        WmsCheckBillRepository.CheckBillRow bill = checkBillRepository.getRow(id);
        if (bill == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "check bill not found");
        }
        List<WmsCheckBillItemResponse> items = checkBillDetailRepository.listRows(id).stream()
                .map(r -> new WmsCheckBillItemResponse(
                        r.getId(),
                        r.getProductId(),
                        r.getProductCode(),
                        r.getProductName(),
                        r.getUnit(),
                        r.getCountedQty(),
                        r.getBookQty(),
                        r.getDiffQty()))
                .toList();
        return new WmsCheckBillDetailResponse(
                bill.getId(),
                bill.getBillNo(),
                bill.getWarehouseId(),
                bill.getWarehouseName(),
                bill.getStatus(),
                bill.getRemark(),
                bill.getCreateBy(),
                bill.getCreateTime(),
                bill.getExecuteTime(),
                items);
    }

    @Transactional
    public WmsCheckBillResponse create(WmsCheckBillCreateRequest request, String createdBy) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        if (request.warehouseId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseId is required");
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lines is required");
        }
        validateLines(request.lines());

        var wh = warehouseRepository.findByIdAndDeleted(request.warehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse not found"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse disabled");
        }

        // Validate products exist + enabled before persisting any data.
        for (WmsCheckBillLineRequest line : request.lines()) {
            var p = productRepository.findByIdAndDeleted(line.productId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found: " + line.productId()));
            if (p.getStatus() != null && p.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product disabled: " + line.productId());
            }
        }

        WmsCheckBill bill = new WmsCheckBill();
        bill.setBillNo(generateBillNo("CK"));
        bill.setWarehouseId(request.warehouseId());
        bill.setStatus(STATUS_PENDING);
        bill.setRemark(trimToNull(request.remark()));
        bill.setCreateBy(trimToNull(createdBy));
        bill.setCreateTime(LocalDateTime.now());
        bill = checkBillRepository.save(bill);

        for (WmsCheckBillLineRequest line : request.lines()) {
            WmsCheckBillDetail d = new WmsCheckBillDetail();
            d.setBillId(bill.getId());
            d.setProductId(line.productId());
            d.setCountedQty(line.countedQty());
            d.setBookQty(BigDecimal.ZERO);
            d.setDiffQty(BigDecimal.ZERO);
            checkBillDetailRepository.save(d);
        }

        WmsCheckBillRepository.CheckBillRow row = checkBillRepository.getRow(bill.getId());
        return new WmsCheckBillResponse(
                row.getId(),
                row.getBillNo(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getStatus(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getExecuteTime());
    }

    @Transactional
    public WmsCheckExecuteResponse execute(Long id, String createdBy) {
        WmsCheckBill bill = checkBillRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "check bill not found"));

        if (Objects.equals(bill.getStatus(), STATUS_COMPLETED)) {
            WmsIoBill in = ioBillRepository.findFirstByBizNoAndType(bill.getBillNo(), BILL_TYPE_STOCK_IN).orElse(null);
            WmsIoBill out = ioBillRepository.findFirstByBizNoAndType(bill.getBillNo(), BILL_TYPE_STOCK_OUT).orElse(null);
            return new WmsCheckExecuteResponse(
                    bill.getId(),
                    bill.getBillNo(),
                    in != null ? in.getId() : null,
                    in != null ? in.getBillNo() : null,
                    out != null ? out.getId() : null,
                    out != null ? out.getBillNo() : null);
        }
        if (!Objects.equals(bill.getStatus(), STATUS_PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid check bill status");
        }

        var wh = warehouseRepository.findByIdAndDeleted(bill.getWarehouseId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse not found"));
        if (wh.getStatus() != null && wh.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouse disabled");
        }

        List<WmsCheckBillDetail> details = checkBillDetailRepository.findByBillId(bill.getId());
        if (details.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "check bill has no details");
        }

        // Validate products exist + enabled before applying any stock changes.
        Set<Long> seen = new HashSet<>();
        for (WmsCheckBillDetail d : details) {
            if (!seen.add(d.getProductId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate productId on bill: " + d.getProductId());
            }
            var p = productRepository.findByIdAndDeleted(d.getProductId(), 0)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found: " + d.getProductId()));
            if (p.getStatus() != null && p.getStatus() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product disabled: " + d.getProductId());
            }
            if (d.getCountedQty() == null || d.getCountedQty().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "countedQty must be >= 0");
            }
        }

        // Compute diffs first + validate deductions against available stock (avoid partial writes).
        List<DiffLine> diffs = new ArrayList<>();
        for (WmsCheckBillDetail d : details) {
            WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(bill.getWarehouseId(), d.getProductId()).orElse(null);
            BigDecimal stockQty = stock == null ? BigDecimal.ZERO : safeQty(stock.getStockQty());
            BigDecimal lockedQty = stock == null ? BigDecimal.ZERO : safeQty(stock.getLockedQty());
            validateStockInvariant(stockQty, lockedQty);

            BigDecimal counted = safeQty(d.getCountedQty());
            BigDecimal diff = counted.subtract(stockQty);
            diffs.add(new DiffLine(d, stockQty, diff));

            if (diff.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal need = diff.abs();
                BigDecimal available = stockQty.subtract(lockedQty);
                if (available.compareTo(need) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "insufficient stock for check adjust-out (productId=" + d.getProductId()
                                    + ", available=" + available + ", required=" + need + ")");
                }
            }
        }

        // Create adjustment bills (optional) for traceability.
        WmsIoBill inBill = null;
        WmsIoBill outBill = null;
        List<DiffLine> inLines = diffs.stream().filter(x -> x.diff.compareTo(BigDecimal.ZERO) > 0).toList();
        List<DiffLine> outLines = diffs.stream().filter(x -> x.diff.compareTo(BigDecimal.ZERO) < 0).toList();

        LocalDateTime now = LocalDateTime.now();
        String createdBy0 = trimToNull(createdBy);

        if (!inLines.isEmpty()) {
            inBill = new WmsIoBill();
            inBill.setBillNo(generateBillNo("CAI"));
            inBill.setType(BILL_TYPE_STOCK_IN);
            inBill.setWarehouseId(bill.getWarehouseId());
            inBill.setStatus(STATUS_COMPLETED);
            inBill.setBizNo(bill.getBillNo()); // link to check bill (do not reuse biz_id to avoid reversal ambiguity)
            inBill.setRemark("盘点调整入库（来源盘点单）: " + bill.getBillNo());
            inBill.setCreateBy(createdBy0);
            inBill.setCreateTime(now);
            inBill = ioBillRepository.save(inBill);
        }
        if (!outLines.isEmpty()) {
            outBill = new WmsIoBill();
            outBill.setBillNo(generateBillNo("CAO"));
            outBill.setType(BILL_TYPE_STOCK_OUT);
            outBill.setWarehouseId(bill.getWarehouseId());
            outBill.setStatus(STATUS_COMPLETED);
            outBill.setBizNo(bill.getBillNo());
            outBill.setRemark("盘点调整出库（来源盘点单）: " + bill.getBillNo());
            outBill.setCreateBy(createdBy0);
            outBill.setCreateTime(now);
            outBill = ioBillRepository.save(outBill);
        }

        // Apply adjustments + write logs.
        for (DiffLine x : diffs) {
            x.detail.setBookQty(x.bookQty);
            x.detail.setDiffQty(x.diff);
            checkBillDetailRepository.save(x.detail);

            if (x.diff.compareTo(BigDecimal.ZERO) == 0) continue;

            if (x.diff.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal qty = x.diff;

                WmsIoBillDetail d = new WmsIoBillDetail();
                d.setBillId(inBill.getId());
                d.setProductId(x.detail.getProductId());
                d.setQty(qty);
                d.setRealQty(qty);
                ioBillDetailRepository.save(d);

                WmsStock stock = upsertIncreaseStockWithRetry(bill.getWarehouseId(), x.detail.getProductId(), qty);
                WmsStockLog log = new WmsStockLog();
                log.setWarehouseId(bill.getWarehouseId());
                log.setProductId(x.detail.getProductId());
                log.setBizType("CHECK_ADJUST_IN");
                log.setBizNo(inBill.getBillNo());
                log.setChangeQty(qty);
                log.setAfterStockQty(safeQty(stock.getStockQty()));
                log.setCreateTime(LocalDateTime.now());
                stockLogRepository.save(log);
            } else {
                BigDecimal qty = x.diff.abs();

                WmsIoBillDetail d = new WmsIoBillDetail();
                d.setBillId(outBill.getId());
                d.setProductId(x.detail.getProductId());
                d.setQty(qty);
                d.setRealQty(qty);
                ioBillDetailRepository.save(d);

                WmsStock stock = deductStockWithRetry(bill.getWarehouseId(), x.detail.getProductId(), qty);
                WmsStockLog log = new WmsStockLog();
                log.setWarehouseId(bill.getWarehouseId());
                log.setProductId(x.detail.getProductId());
                log.setBizType("CHECK_ADJUST_OUT");
                log.setBizNo(outBill.getBillNo());
                log.setChangeQty(qty.negate());
                log.setAfterStockQty(safeQty(stock.getStockQty()));
                log.setCreateTime(LocalDateTime.now());
                stockLogRepository.save(log);
            }
        }

        bill.setStatus(STATUS_COMPLETED);
        bill.setExecuteTime(LocalDateTime.now());
        checkBillRepository.save(bill);

        return new WmsCheckExecuteResponse(
                bill.getId(),
                bill.getBillNo(),
                inBill != null ? inBill.getId() : null,
                inBill != null ? inBill.getBillNo() : null,
                outBill != null ? outBill.getId() : null,
                outBill != null ? outBill.getBillNo() : null);
    }

    private WmsStock upsertIncreaseStockWithRetry(Long warehouseId, Long productId, BigDecimal qty) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return upsertIncreaseStockOnce(warehouseId, productId, qty);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempts >= 3) throw e;
            }
        }
    }

    private WmsStock upsertIncreaseStockOnce(Long warehouseId, Long productId, BigDecimal qty) {
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
        stock.setStockQty(stockQty.subtract(qty));
        stock.setLockedQty(lockedQty);
        stock.setUpdateTime(LocalDateTime.now());
        validateStockInvariant(stock.getStockQty(), stock.getLockedQty());
        return stockRepository.saveAndFlush(stock);
    }

    private static void validateLines(List<WmsCheckBillLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (WmsCheckBillLineRequest line : lines) {
            if (line == null || line.productId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
            }
            if (!seen.add(line.productId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate productId: " + line.productId());
            }
            if (line.countedQty() == null || line.countedQty().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "countedQty must be >= 0");
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

    private static String generateBillNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return prefix + ts + "-" + rand;
    }

    private record DiffLine(WmsCheckBillDetail detail, BigDecimal bookQty, BigDecimal diff) {
    }
}

