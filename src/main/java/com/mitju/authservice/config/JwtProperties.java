package com.mitju.authservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Binds jwt.* from application.yml into a single strongly-typed bean.
 * Fails fast on startup if any value is missing or invalid.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    /** Base64-encoded HMAC-SHA256 secret. Min 64 chars encoded = 48 bytes decoded. */
    @NotBlank(message = "jwt.secret must not be blank")
    private String secret;

    /** Access token TTL in seconds. Default 900 (15 min). */
    @Min(value = 60, message = "jwt.access-token-expiry-seconds must be >= 60")
    private long accessTokenExpirySeconds = 900L;

    /** Refresh token TTL in seconds. Default 2592000 (30 days). */
    @Min(value = 3600, message = "jwt.refresh-token-expiry-seconds must be >= 3600")
    private long refreshTokenExpirySeconds = 2_592_000L;

    /** JWT issuer claim (iss). */
    @NotBlank
    private String issuer = "mitju.com";
}
