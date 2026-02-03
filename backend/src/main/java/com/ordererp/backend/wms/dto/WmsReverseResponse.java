package com.ordererp.backend.wms.dto;

public record WmsReverseResponse(
        Long reversalBillId,
        String reversalBillNo) {
}

