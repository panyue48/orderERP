package com.ordererp.backend.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FinAccountCreateRequest(
        @NotBlank String accountName,
        String accountNo,
        @NotNull BigDecimal openingBalance,
        String remark) {
}

