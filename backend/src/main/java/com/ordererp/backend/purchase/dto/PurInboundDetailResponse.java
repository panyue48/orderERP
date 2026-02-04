package com.ordererp.backend.purchase.dto;

import java.util.List;

public record PurInboundDetailResponse(
        PurInboundResponse inbound,
        List<PurInboundItemResponse> items) {
}

