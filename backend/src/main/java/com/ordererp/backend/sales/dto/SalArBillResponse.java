package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalArBillResponse(
        Long id,
        String billNo,
        Long customerId,
        String customerCode,
        String customerName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalAmount,
        BigDecimal receivedAmount,
        BigDecimal invoiceAmount,
        Integer status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String auditBy,
        LocalDateTime auditTime) {
}

