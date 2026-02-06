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
        Integer qcStatus,
        String qcBy,
        LocalDateTime qcTime,
        String qcRemark,
        Long wmsBillId,
        String wmsBillNo,
        Integer reverseStatus,
        String reverseBy,
        LocalDateTime reverseTime,
        Long reverseWmsBillId,
        String reverseWmsBillNo,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String executeBy,
        LocalDateTime executeTime) {
}
