package com.ordererp.backend.purchase.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.common.dto.ImportResult;
import com.ordererp.backend.common.dto.ImportResult.RowError;
import com.ordererp.backend.purchase.entity.PurOrder;
import com.ordererp.backend.purchase.entity.PurOrderDetail;
import com.ordererp.backend.purchase.excel.PurOrderExcelRow;
import com.ordererp.backend.purchase.repository.PurOrderDetailRepository;
import com.ordererp.backend.purchase.repository.PurOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PurOrderExcelService {
    private static final int ORDER_STATUS_AUDITED = 2;

    private static final int PARTNER_TYPE_SUPPLIER = 1;

    private final PurOrderRepository orderRepository;
    private final PurOrderDetailRepository detailRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseProductRepository productRepository;
    private final TransactionTemplate txTemplate;

    public PurOrderExcelService(PurOrderRepository orderRepository, PurOrderDetailRepository detailRepository,
            BasePartnerRepository partnerRepository, BaseProductRepository productRepository, PlatformTransactionManager txManager) {
        this.orderRepository = orderRepository;
        this.detailRepository = detailRepository;
        this.partnerRepository = partnerRepository;
        this.productRepository = productRepository;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public Page<PurOrderExcelRow> exportOrders(String keyword, Pageable pageable) {
        return detailRepository.exportRows(trimToNull(keyword), pageable).map(r -> {
            PurOrderExcelRow row = new PurOrderExcelRow();
            row.setOrderNo(r.getOrderNo());
            row.setSupplierCode(r.getSupplierCode());
            row.setSupplierName(r.getSupplierName());
            row.setOrderDate(r.getOrderDate());
            row.setStatus(r.getStatus());
            row.setProductCode(r.getProductCode());
            row.setProductName(r.getProductName());
            row.setUnit(r.getUnit());
            row.setPrice(r.getPrice());
            row.setQty(r.getQty());
            row.setAmount(r.getAmount());
            row.setInQty(r.getInQty());
            row.setRemark(r.getRemark());
            return row;
        });
    }

    /**
     * 导入采购订单：每一行作为一张采购单的一个明细行；若同一 Excel 中多行的“采购单号”相同，则会被合并为一张采购单。
     *
     * <p>导入时默认将采购单直接置为“已审核”（便于后续收货/对账验证）。</p>
     */
    public ImportResult importOrders(List<PurOrderExcelRow> rows, String operator) {
        List<RowError> errors = new ArrayList<>();
        int inserted = 0;
        int updated = 0;

        if (rows == null || rows.isEmpty()) {
            return ImportResult.ok(0, 0, 0, List.of());
        }

        Map<String, List<RowWithNum>> grouped = new HashMap<>();
        int rowNum = 1;
        for (PurOrderExcelRow r : rows) {
            rowNum++;
            String supplierCode = normalizeCode(r.getSupplierCode());
            String productCode = normalizeCode(r.getProductCode());
            if (supplierCode == null) {
                errors.add(new RowError(rowNum, "supplierCode is required"));
                continue;
            }
            if (productCode == null) {
                errors.add(new RowError(rowNum, "productCode is required"));
                continue;
            }
            BigDecimal qty = safeQty(r.getQty());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new RowError(rowNum, "qty must be > 0"));
                continue;
            }
            BigDecimal price = safeMoney(r.getPrice());
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new RowError(rowNum, "price must be >= 0"));
                continue;
            }

            String orderNo = normalizeCode(r.getOrderNo());
            String key = orderNo == null ? ("AUTO_ROW_" + rowNum) : orderNo.toLowerCase(Locale.ROOT);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new RowWithNum(rowNum, r));
        }

        for (Map.Entry<String, List<RowWithNum>> e : grouped.entrySet()) {
            List<RowWithNum> group = e.getValue();
            if (group == null || group.isEmpty()) continue;

            try {
                boolean ok = txTemplate.execute(status -> {
                    try {
                        createOneOrder(group, operator);
                        return true;
                    } catch (RuntimeException ex) {
                        status.setRollbackOnly();
                        throw ex;
                    }
                });
                if (ok) inserted++;
            } catch (RuntimeException ex) {
                // Report error on the first data row of this group.
                int rn = group.get(0).rowNum;
                errors.add(new RowError(rn, ex.getMessage() == null ? "import failed" : ex.getMessage()));
            }
        }

        return ImportResult.ok(rows.size(), inserted, updated, errors);
    }

    private void createOneOrder(List<RowWithNum> group, String operator) {
        // Validate consistent supplierCode across group.
        String supplierCode = normalizeCode(group.get(0).row.getSupplierCode());
        for (RowWithNum rw : group) {
            String sc = normalizeCode(rw.row.getSupplierCode());
            if (!Objects.equals(supplierCode, sc)) {
                throw new IllegalArgumentException("supplierCode must be consistent within the same orderNo group");
            }
        }

        BasePartner supplier = partnerRepository.findFirstByPartnerCode(supplierCode)
                .orElseThrow(() -> new IllegalArgumentException("unknown supplierCode: " + supplierCode));
        if (!Objects.equals(supplier.getType(), PARTNER_TYPE_SUPPLIER)) {
            throw new IllegalArgumentException("partner is not supplier: " + supplierCode);
        }
        if (supplier.getStatus() != null && supplier.getStatus() != 1) {
            throw new IllegalArgumentException("supplier disabled: " + supplierCode);
        }

        String orderNo = normalizeCode(group.get(0).row.getOrderNo());
        if (orderNo == null) {
            orderNo = generateOrderNo();
        }

        // Prevent duplicate by orderNo.
        if (orderRepository.findFirstByOrderNo(orderNo).orElse(null) != null) {
            throw new IllegalArgumentException("orderNo already exists: " + orderNo);
        }

        LocalDate orderDate = group.get(0).row.getOrderDate();
        if (orderDate == null) orderDate = LocalDate.now();

        String remark = trimToNull(group.get(0).row.getRemark());

        // Ensure unique products in group.
        Map<String, Boolean> seenSku = new HashMap<>();
        for (RowWithNum rw : group) {
            String sku = normalizeCode(rw.row.getProductCode());
            if (sku == null) continue;
            String k = sku.toLowerCase(Locale.ROOT);
            if (seenSku.putIfAbsent(k, true) != null) {
                throw new IllegalArgumentException("duplicate productCode in same order: " + sku);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        PurOrder order = new PurOrder();
        order.setOrderNo(orderNo);
        order.setSupplierId(supplier.getId());
        order.setOrderDate(orderDate);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPayAmount(BigDecimal.ZERO);
        order.setStatus(ORDER_STATUS_AUDITED);
        order.setRemark(remark);
        order.setCreateBy(trimToNull(operator));
        order.setCreateTime(now);
        order.setAuditBy(trimToNull(operator));
        order.setAuditTime(now);
        try {
            order = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("orderNo already exists: " + orderNo);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (RowWithNum rw : group) {
            PurOrderExcelRow r = rw.row;
            String productCode = normalizeCode(r.getProductCode());
            BaseProduct product = productRepository.findFirstByProductCode(productCode)
                    .orElseThrow(() -> new IllegalArgumentException("unknown productCode: " + productCode));
            if (product.getStatus() != null && product.getStatus() != 1) {
                throw new IllegalArgumentException("product disabled: " + productCode);
            }

            BigDecimal qty = safeQty(r.getQty());
            BigDecimal price = safeMoney(r.getPrice());
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
        orderRepository.save(order);
    }

    private record RowWithNum(int rowNum, PurOrderExcelRow row) {
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

    private static String normalizeCode(String value) {
        String s = trimToNull(value);
        if (s == null) return null;
        return s.trim();
    }

    private static String generateOrderNo() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "PO" + date + "-" + rand;
    }
}
