package com.ordererp.backend.purchase.dto;

import java.util.List;

public record PurReturnDetailResponse(
        PurReturnResponse header,
        List<PurReturnItemResponse> items) {
}

