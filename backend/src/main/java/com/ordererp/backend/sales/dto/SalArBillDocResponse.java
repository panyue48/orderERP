package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalArBillDocResponse(
        Long id,
        Integer docType,
        Long docId,
        String docNo,
        Long orderId,
        String orderNo,
        LocalDateTime docTime,
        BigDecimal amount,
        String productSummary) {
}

