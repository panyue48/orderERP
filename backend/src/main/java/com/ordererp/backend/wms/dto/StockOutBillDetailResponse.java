package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockOutBillDetailResponse(
        Long id,
        String billNo,
        Long warehouseId,
        String warehouseName,
        Integer status,
        BigDecimal totalQty,
        String remark,
        String createBy,
        LocalDateTime createTime,
        List<StockOutBillItemResponse> items) {
}

