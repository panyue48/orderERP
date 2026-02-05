package com.ordererp.backend.purchase.dto;

import java.time.LocalDate;

public record PurOrderOptionResponse(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        LocalDate orderDate,
        Integer status) {
}

