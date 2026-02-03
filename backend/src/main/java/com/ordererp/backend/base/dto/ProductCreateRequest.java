package com.ordererp.backend.base.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductCreateRequest(
        Long categoryId,
        @NotBlank @Size(max = 64) String productCode,
        @NotBlank @Size(max = 128) String productName,
        @Size(max = 64) String barcode,
        @Size(max = 128) String spec,
        @Size(max = 32) String unit,
        @Digits(integer = 10, fraction = 3) BigDecimal weight,
        @Digits(integer = 16, fraction = 2) BigDecimal purchasePrice,
        @Digits(integer = 16, fraction = 2) BigDecimal salePrice,
        @Min(0) @Max(999999) Integer lowStock,
        @Size(max = 255) String imageUrl,
        @Min(0) @Max(1) Integer status) {
}
