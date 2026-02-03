package com.ordererp.backend.base.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        Long parentId,
        @NotBlank @Size(max = 64) String categoryCode,
        @NotBlank @Size(max = 128) String categoryName,
        Integer sort,
        @Min(0) @Max(1) Integer status) {
}

