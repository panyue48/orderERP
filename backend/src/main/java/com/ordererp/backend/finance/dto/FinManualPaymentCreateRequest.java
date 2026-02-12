package com.ordererp.backend.finance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinManualPaymentCreateRequest(
        @NotNull Integer type,
        Long partnerId,
        Long accountId,
        @NotNull BigDecimal amount,
        LocalDate payDate,
        String method,
        String bizNo,
        String remark) {
}

