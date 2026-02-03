package com.ordererp.backend.wms.dto;

public record WmsCheckExecuteResponse(
        Long checkBillId,
        String checkBillNo,
        Long stockInBillId,
        String stockInBillNo,
        Long stockOutBillId,
        String stockOutBillNo) {
}

