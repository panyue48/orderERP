package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SalOrderDetailResponse(
        Long id,
        String orderNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long warehouseId,
        String warehouseName,
        LocalDate orderDate,
        BigDecimal totalAmount,
        Integer status,
        String remark,
        Long wmsBillId,
        String wmsBillNo,
        String createBy,
        LocalDateTime createTime,
        String auditBy,
        LocalDateTime auditTime,
        String shipBy,
        LocalDateTime shipTime,
        String cancelBy,
        LocalDateTime cancelTime,
        List<SalOrderItemResponse> items) {
}

