package com.mitju.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/auth/refresh.
 * Client sends the refresh token obtained at login.
 */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        @JsonProperty("refresh_token")
        String refreshToken

) {}
