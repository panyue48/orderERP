package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WmsStockResponse(
        Long id,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal stockQty,
        BigDecimal lockedQty,
        BigDecimal availableQty,
        LocalDateTime updateTime) {
}

