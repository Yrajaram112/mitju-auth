package com.mitju.profileservice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiry;   // seconds
    private final long refreshTokenExpiry;  // seconds

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
            @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    // ── Generate ───────────────────────────────────────────────────────

    public String generateAccessToken(UUID userId, String email, String role) {
        return buildToken(userId.toString(), email, role, accessTokenExpiry * 1000);
    }

    public String generateRefreshToken(UUID userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + refreshTokenExpiry * 1000))
                .claim("type", "refresh")
                .signWith(key)
                .compact();
    }

    // ── Validate & Parse ───────────────────────────────────────────────

    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return validateAndParseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndParseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    private String buildToken(String subject, String email, String role, long expiryMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim("email", email)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(key)
                .compact();
    }
}
