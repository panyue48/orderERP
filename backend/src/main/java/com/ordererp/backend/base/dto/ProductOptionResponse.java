package com.ordererp.backend.base.dto;

public record ProductOptionResponse(Long id, Long categoryId, String productCode, String productName, String unit) {
}
