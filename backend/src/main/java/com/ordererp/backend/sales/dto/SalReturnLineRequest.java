package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;

public record SalReturnLineRequest(
        Long shipDetailId,
        BigDecimal qty) {
}
