package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurApBillResponse(
        Long id,
        String billNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal invoiceAmount,
        Integer status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String auditBy,
        LocalDateTime auditTime) {
}

