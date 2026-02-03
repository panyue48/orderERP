package com.ordererp.backend.base.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseCreateRequest(
        @NotBlank @Size(max = 32) String warehouseCode,
        @NotBlank @Size(max = 64) String warehouseName,
        @Size(max = 255) String location,
        @Size(max = 32) String manager,
        @Min(0) @Max(1) Integer status) {
}

