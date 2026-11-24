package com.relay.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String username) {

    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresIn,
                                      UUID userId, String username) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, userId, username);
    }
}
