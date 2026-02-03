package com.ordererp.backend.system.dto.admin;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RoleMenuUpdateRequest(@NotNull List<Long> menuIds) {
}

