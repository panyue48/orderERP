package com.ordererp.backend.wms.dto;

import java.util.List;

public record WmsBillPrecheckResponse(
        boolean ok,
        String message,
        List<WmsBillPrecheckLine> lines) {
}

