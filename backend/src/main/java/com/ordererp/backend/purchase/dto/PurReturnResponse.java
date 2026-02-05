package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurReturnResponse(
        Long id,
        String returnNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        Long warehouseId,
        String warehouseName,
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
        String executeBy,
        LocalDateTime executeTime) {
}

