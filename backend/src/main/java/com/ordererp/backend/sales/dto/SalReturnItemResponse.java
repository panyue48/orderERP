package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;

public record SalReturnItemResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal price,
        BigDecimal qty,
        BigDecimal amount) {
}

