package com.ordererp.backend.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinPaymentResponse(
        Long id,
        String payNo,
        Integer type,
        Long partnerId,
        String partnerName,
        Long accountId,
        String accountName,
        BigDecimal amount,
        Integer bizType,
        Long bizId,
        String bizNo,
        LocalDate payDate,
        String method,
        String remark,
        Integer status,
        String createBy,
        LocalDateTime createTime,
        String cancelBy,
        LocalDateTime cancelTime) {
}

