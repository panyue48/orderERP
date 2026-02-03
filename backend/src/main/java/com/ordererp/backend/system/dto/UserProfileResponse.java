package com.ordererp.backend.system.dto;

public record UserProfileResponse(Long id, String username, String nickname, String email, String phone,
        String avatar) {
}

