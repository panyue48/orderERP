package com.ordererp.backend.purchase.dto;

public record PurOrderInboundResponse(
        Long orderId,
        String orderNo,
        Integer orderStatus,
        Long wmsBillId,
        String wmsBillNo) {
}

