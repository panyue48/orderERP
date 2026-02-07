package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;

public record SalCreditUsageResponse(
        Long customerId,
        BigDecimal creditLimit,
        BigDecimal usedAmount,
        BigDecimal availableAmount,
        BigDecimal outstandingArAmount,
        BigDecimal unbilledNetAmount,
        BigDecimal openOrderReservedAmount,
        Boolean enabled) {
}

