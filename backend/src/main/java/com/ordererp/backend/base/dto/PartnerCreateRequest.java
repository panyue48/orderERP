package com.ordererp.backend.base.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PartnerCreateRequest(
        @NotBlank @Size(max = 128) String partnerName,
        @NotBlank @Size(max = 64) String partnerCode,
        @NotNull @Min(1) @Max(2) Integer type,
        @Size(max = 64) String contact,
        @Size(max = 32) String phone,
        @Size(max = 64) String email,
        @Digits(integer = 16, fraction = 2) BigDecimal creditLimit,
        @Min(0) @Max(1) Integer status) {
}

