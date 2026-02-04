package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;

public record PurInboundItemResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal planQty,
        BigDecimal realQty) {
}

