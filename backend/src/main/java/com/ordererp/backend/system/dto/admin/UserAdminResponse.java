package com.ordererp.backend.system.dto.admin;

import java.util.List;

public record UserAdminResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String avatar,
        Integer status,
        List<Long> roleIds,
        List<String> roleKeys) {
}

