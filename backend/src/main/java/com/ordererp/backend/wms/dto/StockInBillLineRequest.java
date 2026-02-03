package com.ordererp.backend.wms.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record StockInBillLineRequest(
        @NotNull Long productId,
        @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal qty) {
}

