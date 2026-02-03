package com.ordererp.backend.base.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        Long categoryId,
        String productCode,
        String productName,
        String barcode,
        String spec,
        String unit,
        BigDecimal weight,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        Integer lowStock,
        String imageUrl,
        Integer status) {
}
