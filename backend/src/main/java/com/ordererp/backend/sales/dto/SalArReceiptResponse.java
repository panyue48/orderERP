package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalArReceiptResponse(
        Long id,
        String receiptNo,
        Long billId,
        Long customerId,
        LocalDate receiptDate,
        BigDecimal amount,
        String method,
        String remark,
        Integer status,
        String createBy,
        LocalDateTime createTime,
        String cancelBy,
        LocalDateTime cancelTime) {
}

