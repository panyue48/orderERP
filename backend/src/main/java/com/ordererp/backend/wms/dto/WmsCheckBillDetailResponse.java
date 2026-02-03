package com.ordererp.backend.wms.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WmsCheckBillDetailResponse(
        Long id,
        String billNo,
        Long warehouseId,
        String warehouseName,
        Integer status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        LocalDateTime executeTime,
        List<WmsCheckBillItemResponse> items) {
}

