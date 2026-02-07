package com.ordererp.backend.sales.repository;

import java.math.BigDecimal;

public interface CustomerAmountRow {
    Long getCustomerId();

    BigDecimal getAmount();
}

