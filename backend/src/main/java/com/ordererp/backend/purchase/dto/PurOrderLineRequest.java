package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;

public record PurOrderLineRequest(
        Long productId,
        BigDecimal price,
        BigDecimal qty) {
}

