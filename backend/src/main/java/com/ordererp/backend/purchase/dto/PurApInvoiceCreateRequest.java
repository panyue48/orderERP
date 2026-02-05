package com.ordererp.backend.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PurApInvoiceCreateRequest(
        @NotBlank String invoiceNo,
        LocalDate invoiceDate,
        @NotNull BigDecimal amount,
        BigDecimal taxAmount,
        String remark) {
}

