package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalShipResponse(
        Long id,
        String shipNo,
        Long orderId,
        String orderNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long warehouseId,
        String warehouseName,
        LocalDateTime shipTime,
        BigDecimal totalQty,
        Long wmsBillId,
        String wmsBillNo,
        Integer reverseStatus,
        String reverseBy,
        LocalDateTime reverseTime,
        Long reverseWmsBillId,
        String reverseWmsBillNo,
        String createBy,
        LocalDateTime createTime) {
}
