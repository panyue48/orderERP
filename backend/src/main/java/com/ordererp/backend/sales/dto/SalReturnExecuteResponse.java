package com.ordererp.backend.sales.dto;

public record SalReturnExecuteResponse(
        Long returnId,
        String returnNo,
        Integer status,
        Long wmsBillId,
        String wmsBillNo) {
}

