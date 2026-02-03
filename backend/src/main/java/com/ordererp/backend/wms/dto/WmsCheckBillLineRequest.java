package com.ordererp.backend.wms.dto;

import java.math.BigDecimal;

public record WmsCheckBillLineRequest(
        Long productId,
        BigDecimal countedQty) {
}

