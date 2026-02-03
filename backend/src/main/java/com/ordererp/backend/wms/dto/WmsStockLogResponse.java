package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WmsStockLogResponse(
        Long id,
        Long warehouseId,
        String warehouseName,
        Long productId,
        String productCode,
        String productName,
        String bizType,
        String bizNo,
        BigDecimal changeQty,
        BigDecimal afterStockQty,
        LocalDateTime createTime) {
}

