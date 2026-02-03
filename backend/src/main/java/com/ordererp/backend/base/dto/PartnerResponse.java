package com.ordererp.backend.base.dto;

import java.math.BigDecimal;

public record PartnerResponse(
        Long id,
        String partnerName,
        String partnerCode,
        Integer type,
        String contact,
        String phone,
        String email,
        BigDecimal creditLimit,
        Integer status) {
}

