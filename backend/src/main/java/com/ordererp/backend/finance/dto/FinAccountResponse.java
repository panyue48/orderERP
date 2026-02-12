package com.ordererp.backend.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinAccountResponse(
        Long id,
        String accountName,
        String accountNo,
        BigDecimal balance,
        String remark,
        Integer status,
        String createBy,
        LocalDateTime createTime,
        String updateBy,
        LocalDateTime updateTime) {
}

