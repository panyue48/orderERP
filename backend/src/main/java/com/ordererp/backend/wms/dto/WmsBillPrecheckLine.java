package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;

public record WmsBillPrecheckLine(
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal qty,
        BigDecimal stockQty,
        BigDecimal lockedQty,
        BigDecimal availableQty,
        boolean ok,
        String message) {
}

