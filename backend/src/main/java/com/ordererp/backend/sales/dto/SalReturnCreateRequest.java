package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record SalReturnCreateRequest(
        @NotNull Long shipId,
        @NotNull Long customerId,
        @NotNull Long warehouseId,
        LocalDate returnDate,
        String remark,
        @NotNull List<SalReturnLineRequest> lines) {
}
