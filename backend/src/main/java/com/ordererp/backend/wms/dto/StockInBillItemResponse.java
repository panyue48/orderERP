package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;

public record StockInBillItemResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal qty,
        BigDecimal realQty) {
}

