package com.ordererp.backend.sales.dto;

import java.time.LocalDate;

public record SalOrderOptionResponse(
        Long id,
        String orderNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long warehouseId,
        String warehouseName,
        LocalDate orderDate,
        Integer status) {
}

