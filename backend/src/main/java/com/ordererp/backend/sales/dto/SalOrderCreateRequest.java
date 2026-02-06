package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalOrderCreateRequest(
        @NotNull Long customerId,
        @NotNull Long warehouseId,
        LocalDate orderDate,
        String remark,
        @Size(min = 1) List<SalOrderLineRequest> lines) {
    public record SalOrderLineRequest(
            @NotNull Long productId,
            @NotNull BigDecimal qty,
            BigDecimal price) {
    }
}

