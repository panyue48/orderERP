package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurOrderDetailResponse(
        Long id,
        String orderNo,
        Long supplierId,
        String supplierCode,
        String supplierName,
        LocalDate orderDate,
        BigDecimal totalAmount,
        BigDecimal payAmount,
        Integer status,
        String remark,
        String createBy,
        LocalDateTime createTime,
        String auditBy,
        LocalDateTime auditTime,
        List<PurOrderItemResponse> items) {
}

