package com.ordererp.backend.system.dto.admin;

import jakarta.validation.constraints.Size;

public record RoleAdminUpdateRequest(
        @Size(max = 50) String roleName,
        @Size(max = 50) String roleKey,
        Integer sort,
        Integer status) {
}

