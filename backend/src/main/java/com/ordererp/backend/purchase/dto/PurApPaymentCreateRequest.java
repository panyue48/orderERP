package com.ordererp.backend.purchase.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PurApPaymentCreateRequest(
        LocalDate payDate,
        @NotNull BigDecimal amount,
        String method,
        String remark) {
}

