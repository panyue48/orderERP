package com.ordererp.backend.finance.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinTransferCreateRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull BigDecimal amount,
        LocalDate transferDate,
        String remark) {
}

