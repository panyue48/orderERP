package com.ordererp.backend.sales.dto;

import jakarta.validation.constraints.NotBlank;

public record SalReturnQcRejectRequest(
        @NotBlank String disposition,
        String remark) {
}

