package com.ordererp.backend.finance.dto;

import java.math.BigDecimal;

public record FinAccountOptionResponse(
        Long id,
        String accountName,
        BigDecimal balance) {
}

