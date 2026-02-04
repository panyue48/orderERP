package com.ordererp.backend.purchase.dto;

public record PurInboundExecuteResponse(
        Long inboundId,
        String inboundNo,
        Long orderId,
        String orderNo,
        Integer orderStatus,
        Long wmsBillId,
        String wmsBillNo) {
}

