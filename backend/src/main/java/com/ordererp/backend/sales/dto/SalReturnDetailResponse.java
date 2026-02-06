package com.ordererp.backend.sales.dto;

import java.util.List;

public record SalReturnDetailResponse(
        SalReturnResponse header,
        List<SalReturnItemResponse> items) {
}

