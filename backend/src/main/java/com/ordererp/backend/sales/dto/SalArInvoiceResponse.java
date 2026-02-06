package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalArInvoiceResponse(
        Long id,
        String invoiceNo,
        Long billId,
        Long customerId,
        LocalDate invoiceDate,
        BigDecimal amount,
        BigDecimal taxAmount,
        String remark,
        Integer status,
        String createBy,
        LocalDateTime createTime,
        String cancelBy,
        LocalDateTime cancelTime) {
}

