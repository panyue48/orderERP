package com.ordererp.backend.base.dto;

public record CategoryResponse(
        Long id,
        Long parentId,
        String categoryCode,
        String categoryName,
        Integer sort,
        Integer status) {
}

