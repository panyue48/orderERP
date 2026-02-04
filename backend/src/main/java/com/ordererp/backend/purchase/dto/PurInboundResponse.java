package com.ordererp.backend.purchase.dto;

import java.time.LocalDateTime;

public record PurInboundResponse(
        Long id,
        String inboundNo,
        String requestNo,
        Long orderId,
        String orderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long warehouseId,
        String warehouseName,
        Integer status,
        Long wmsBillId,
        String wmsBillNo,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String executeBy,
        LocalDateTime executeTime) {
}

