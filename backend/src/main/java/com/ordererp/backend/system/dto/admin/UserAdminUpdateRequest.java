package com.ordererp.backend.system.dto.admin;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UserAdminUpdateRequest(
        @Size(max = 50) String nickname,
        @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String avatar,
        Integer status,
        List<Long> roleIds) {
}

