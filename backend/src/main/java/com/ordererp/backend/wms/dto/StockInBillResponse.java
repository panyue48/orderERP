package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockInBillResponse(
        Long id,
        String billNo,
        Long warehouseId,
        String warehouseName,
        Integer status,
        BigDecimal totalQty,
        String remark,
        String createBy,
        LocalDateTime createTime) {
}

