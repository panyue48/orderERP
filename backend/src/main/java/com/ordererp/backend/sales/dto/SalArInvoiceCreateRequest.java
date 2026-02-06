package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SalArInvoiceCreateRequest(
        @NotNull String invoiceNo,
        LocalDate invoiceDate,
        @NotNull BigDecimal amount,
        BigDecimal taxAmount,
        String remark) {
}

