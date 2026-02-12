package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SalArReceiptCreateRequest(
        LocalDate receiptDate,
        @NotNull BigDecimal amount,
        Long accountId,
        String method,
        String remark) {
}
