package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;

public record PurInboundCreateLineRequest(Long productId, BigDecimal qty) {
}

