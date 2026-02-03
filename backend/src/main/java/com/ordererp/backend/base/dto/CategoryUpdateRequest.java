package com.ordererp.backend.base.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        Long parentId,
        @Size(max = 64) String categoryCode,
        @Size(max = 128) String categoryName,
        Integer sort,
        @Min(0) @Max(1) Integer status) {
}

