package com.ordererp.backend.system.dto.admin;

public record MenuAdminResponse(
        Long id,
        Long parentId,
        String menuName,
        String path,
        String component,
        String perms,
        String icon,
        String menuType,
        Integer sort,
        Integer visible) {
}

