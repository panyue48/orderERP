package com.ordererp.backend.common.dto;

import java.util.List;

public record ImportResult(int total, int inserted, int updated, int failed, List<RowError> errors) {
    public static ImportResult ok(int total, int inserted, int updated, List<RowError> errors) {
        int failed = errors == null ? 0 : errors.size();
        return new ImportResult(total, inserted, updated, failed, errors == null ? List.of() : errors);
    }

    public record RowError(int rowNum, String message) {
    }
}

