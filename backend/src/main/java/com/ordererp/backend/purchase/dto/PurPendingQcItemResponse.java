package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;

public record PurPendingQcItemResponse(
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal qty) {
}

