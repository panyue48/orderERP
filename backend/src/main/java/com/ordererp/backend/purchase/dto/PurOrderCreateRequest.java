package com.ordererp.backend.purchase.dto;

import java.time.LocalDate;
import java.util.List;

public record PurOrderCreateRequest(
        Long supplierId,
        LocalDate orderDate,
        String remark,
        List<PurOrderLineRequest> lines) {
}

