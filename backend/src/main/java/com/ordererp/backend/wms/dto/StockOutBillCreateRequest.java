package com.ordererp.backend.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StockOutBillCreateRequest(
        @NotNull Long warehouseId,
        @Size(max = 255) String remark,
        @NotEmpty @Valid List<StockOutBillLineRequest> lines) {
}

