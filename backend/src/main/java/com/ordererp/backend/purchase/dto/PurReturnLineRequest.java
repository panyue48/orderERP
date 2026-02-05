package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;

public record PurReturnLineRequest(Long productId, BigDecimal price, BigDecimal qty) {
}

