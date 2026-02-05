package com.ordererp.backend.purchase.dto;

public record PurReturnExecuteResponse(
        Long returnId,
        String returnNo,
        Integer returnStatus,
        Long wmsBillId,
        String wmsBillNo) {
}

