package com.ordererp.backend.purchase.dto;

public record PurInboundReverseResponse(
        Long inboundId,
        String inboundNo,
        Integer inboundStatus,
        Long orderId,
        String orderNo,
        Integer orderStatus,
        Long reversalWmsBillId,
        String reversalWmsBillNo) {
}

