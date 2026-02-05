package com.ordererp.backend.purchase.dto;

import java.time.LocalDate;
import java.util.List;

public record PurReturnCreateRequest(
        Long supplierId,
        Long warehouseId,
        LocalDate returnDate,
        String remark,
        List<PurReturnLineRequest> lines) {
}

