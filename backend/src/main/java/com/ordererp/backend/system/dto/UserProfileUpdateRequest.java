package com.ordererp.backend.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserProfileUpdateRequest(
        @Size(max = 50) String nickname,
        @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String avatar) {
}

