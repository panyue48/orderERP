package com.ordererp.backend.wms.dto;

import java.time.LocalDateTime;

public record WmsCheckBillResponse(
        Long id,
        String billNo,
        Long warehouseId,
        String warehouseName,
        Integer status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        LocalDateTime executeTime) {
}

