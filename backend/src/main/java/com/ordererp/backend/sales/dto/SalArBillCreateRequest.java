package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SalArBillCreateRequest(
        @NotNull Long customerId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String remark) {
}

