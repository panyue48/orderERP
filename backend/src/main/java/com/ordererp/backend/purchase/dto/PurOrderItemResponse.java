package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;

public record PurOrderItemResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal amount,
        BigDecimal inQty) {
}

