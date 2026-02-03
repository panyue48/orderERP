package com.ordererp.backend.base.dto;

public record WarehouseResponse(
        Long id,
        String warehouseCode,
        String warehouseName,
        String location,
        String manager,
        Integer status) {
}

