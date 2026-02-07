package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record SalShipBatchRequest(
        @NotBlank String requestNo,
        @Size(min = 1) List<SalShipLineRequest> lines) {
    public record SalShipLineRequest(
            @NotNull Long orderDetailId,
            @NotNull Long productId,
            @NotNull BigDecimal qty) {
    }
}
