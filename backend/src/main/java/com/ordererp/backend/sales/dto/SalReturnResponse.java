package com.ordererp.backend.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalReturnResponse(
        Long id,
        String returnNo,
        Long customerId,
        String customerCode,
        String customerName,
        Long warehouseId,
        String warehouseName,
        Long shipId,
        String shipNo,
        Long orderId,
        String orderNo,
        LocalDate returnDate,
        BigDecimal totalQty,
        BigDecimal totalAmount,
        Integer status,
        String remark,
        Long wmsBillId,
        String wmsBillNo,
        String createBy,
        LocalDateTime createTime,
        String auditBy,
        LocalDateTime auditTime,
        String receiveBy,
        LocalDateTime receiveTime,
        String qcBy,
        LocalDateTime qcTime,
        String qcDisposition,
        String qcRemark,
        String executeBy,
        LocalDateTime executeTime) {
}
