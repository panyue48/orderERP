package com.ordererp.backend.sales.dto;

import java.util.List;

public record SalShipDetailResponse(
        SalShipResponse header,
        List<SalShipItemResponse> items) {
}

