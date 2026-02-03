package com.ordererp.backend.system.dto;

public record LoginResponse(String token, UserInfo user) {
}
