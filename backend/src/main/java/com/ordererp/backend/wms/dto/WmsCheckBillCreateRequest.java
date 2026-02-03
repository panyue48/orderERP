package com.ordererp.backend.wms.dto;

import java.util.List;

public record WmsCheckBillCreateRequest(
        Long warehouseId,
        String remark,
        List<WmsCheckBillLineRequest> lines) {
}

