package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurApPaymentResponse(
        Long id,
        String payNo,
        Long billId,
        Long supplierId,
        LocalDate payDate,
        BigDecimal amount,
        String method,
        String remark,
        Integer status,
        String createBy,
        LocalDateTime createTime,
        String cancelBy,
        LocalDateTime cancelTime) {
}

