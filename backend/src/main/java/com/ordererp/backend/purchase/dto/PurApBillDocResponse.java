package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurApBillDocResponse(
        Long id,
        Integer docType,
        Long docId,
        String docNo,
        Long orderId,
        String orderNo,
        LocalDateTime docTime,
        BigDecimal amount) {
}

