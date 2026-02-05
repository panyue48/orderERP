package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurApInvoiceResponse(
        Long id,
        String invoiceNo,
        Long billId,
        Long supplierId,
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

