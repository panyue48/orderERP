package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurPendingQcSummaryResponse(
        Long orderId,
        Long pendingCount,
        BigDecimal pendingQty,
        List<PurPendingQcItemResponse> items) {
}

