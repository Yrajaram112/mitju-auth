package com.mitju.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mitju.authservice.entity.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned on every successful authentication (register, login, refresh).
 *
 * accessToken  — short-lived JWT (15 min). Send in Authorization: Bearer <token>.
 * refreshToken — long-lived JWT (30 days). Store securely (httpOnly cookie or
 *                secure storage). Use POST /api/auth/refresh to rotate.
 * expiresAt    — absolute UTC instant the access token expires. Frontend can
 *                schedule a silent refresh 60s before this.
 */
public record AuthResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn,           // seconds — convenience for OAuth2 clients

        @JsonProperty("expires_at")
        Instant expiresAt,        // absolute UTC instant

        @JsonProperty("user_id")
        UUID userId,

        @JsonProperty("email")
        String email,

        @JsonProperty("role")
        User.UserRole role

) {
    /** Factory — called from AuthService after token generation. */
    public static AuthResponse of(
            String accessToken,
            String refreshToken,
            long accessTokenExpirySeconds,
            UUID userId,
            String email,
            User.UserRole role
    ) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessTokenExpirySeconds,
                Instant.now().plusSeconds(accessTokenExpirySeconds),
                userId,
                email,
                role
        );
    }
}
