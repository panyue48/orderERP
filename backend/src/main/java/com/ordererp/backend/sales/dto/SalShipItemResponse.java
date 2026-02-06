package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;

public record SalShipItemResponse(
        Long id,
        Long shipId,
        Long orderId,
        Long orderDetailId,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal qty) {
}

