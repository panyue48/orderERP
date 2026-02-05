package com.ordererp.backend.purchase.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PurApBillCreateRequest(
        @NotNull Long supplierId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String remark) {
}

