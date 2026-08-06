package com.sewamobil.dto;

public record AuthResponse(
        String token,
        UserResponse user
) {
}
