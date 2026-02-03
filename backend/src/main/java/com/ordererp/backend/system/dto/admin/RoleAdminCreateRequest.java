package com.ordererp.backend.system.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleAdminCreateRequest(
        @NotBlank @Size(max = 50) String roleName,
        @NotBlank @Size(max = 50) String roleKey,
        Integer sort,
        Integer status) {
}

