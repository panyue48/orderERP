package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;

public record WmsCheckBillItemResponse(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unit,
        BigDecimal countedQty,
        BigDecimal bookQty,
        BigDecimal diffQty) {
}

