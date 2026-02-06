package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SalArReceiptCreateRequest(
        @NotNull LocalDate receiptDate,
        @NotNull BigDecimal amount,
        String method,
        String remark) {
}

