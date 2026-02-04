package com.ordererp.backend.purchase.dto;

import java.util.List;

public record PurInboundCreateRequest(
        String requestNo,
        Long warehouseId,
        String remark,
        List<PurInboundCreateLineRequest> lines) {
}

