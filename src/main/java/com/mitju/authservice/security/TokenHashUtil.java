package com.mitju.authservice.security;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for hashing refresh tokens before DB storage.
 *
 * Why: storing raw JWT strings in the DB means a DB read leak =
 * an attacker can impersonate any user. SHA-256 hashing is a
 * standard mitigation (same approach as GitHub personal access tokens).
 *
 * Usage:
 *   String raw  = jwtService.generateRefreshToken(userId);
 *   String hash = TokenHashUtil.sha256Hex(raw);
 *   // store hash in DB, return raw to client
 */
@Slf4j
public final class TokenHashUtil {

    private TokenHashUtil() {}

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of the input string.
     * Thread-safe: MessageDigest.getInstance() creates a new instance each call.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec — this can never happen
            log.error("[TokenHash] SHA-256 algorithm not available — JVM misconfiguration", e);
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
