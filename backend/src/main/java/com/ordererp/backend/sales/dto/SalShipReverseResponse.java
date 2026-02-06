package com.ordererp.backend.sales.dto;

public record SalShipReverseResponse(
        Long shipId,
        String shipNo,
        Integer reverseStatus,
        Long reverseWmsBillId,
        String reverseWmsBillNo) {
}

