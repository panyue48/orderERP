package com.ordererp.backend.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinTransferResponse(
        Long id,
        String transferNo,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        LocalDate transferDate,
        String remark,
        Integer status,
        String createBy,
        LocalDateTime createTime,
        String cancelBy,
        LocalDateTime cancelTime) {
}

