package com.ordererp.backend.system.dto.admin;

public record RoleAdminResponse(Long id, String roleName, String roleKey, Integer sort, Integer status) {
}

