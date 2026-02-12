package com.ordererp.backend.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinManualPaymentResponse(
        Long id,
        String manualNo,
        Integer type,
        Long partnerId,
        Long accountId,
        BigDecimal amount,
        LocalDate payDate,
        String method,
        String bizNo,
        String remark,
        Integer status,
        String createBy,
        LocalDateTime createTime,
        String cancelBy,
        LocalDateTime cancelTime) {
}

