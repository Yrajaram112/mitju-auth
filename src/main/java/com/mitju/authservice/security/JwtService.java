package com.mitju.authservice.security;

import com.mitju.authservice.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Central JWT factory and validator.
 *
 * Key points:
 * - Secret decoded from Base64 (NOT raw bytes — fixes original bug where
 *   non-ASCII chars in the secret produced a different-length key).
 * - Access tokens carry userId, email, role and type="access".
 * - Refresh tokens carry only userId and type="refresh" — minimise claims exposure.
 * - validateAccessToken() explicitly rejects tokens with type != "access",
 *   preventing a refresh token from being used as an access token (and vice versa).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Decode Base64 secret → raw bytes → HMAC-SHA-256 key
        // If the secret is not valid Base64 this fails at startup — which is correct.
        byte[] keyBytes = Decoders.BASE64.decode(props.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("[JWT] Signing key initialised. Access TTL={}s  Refresh TTL={}s",
                props.getAccessTokenExpirySeconds(), props.getRefreshTokenExpirySeconds());
    }

    // ── Token generation ───────────────────────────────────────────────────

    /**
     * Issues a short-lived access token.
     * Claims: sub=userId, email, role, type=access, iss, iat, exp.
     */
    public String generateAccessToken(UUID userId, String email, String role) {
        long nowMs = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + props.getAccessTokenExpirySeconds() * 1_000L))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Issues an opaque-style refresh token (it is a JWT but carries no PII).
     * The raw string is stored SHA-256-hashed in auth.refresh_tokens.
     */
    public String generateRefreshToken(UUID userId) {
        long nowMs = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + props.getRefreshTokenExpirySeconds() * 1_000L))
                .signWith(signingKey)
                .compact();
    }

    // ── Validation ─────────────────────────────────────────────────────────

    /**
     * Parses and validates an access token.
     * Throws {@link JwtAuthException} with a specific message on any failure.
     * The caller (JwtAuthFilter) catches this and writes the correct HTTP error.
     */
    public Claims validateAccessToken(String token) {
        Claims claims = parseAndVerify(token);
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            log.warn("[JWT] Token type mismatch: expected 'access', got '{}'", type);
            throw new JwtAuthException("Invalid token type");
        }
        return claims;
    }

    /**
     * Parses and validates a refresh token.
     * Throws {@link JwtAuthException} on failure.
     */
    public Claims validateRefreshToken(String token) {
        Claims claims = parseAndVerify(token);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            log.warn("[JWT] Token type mismatch: expected 'refresh', got '{}'", type);
            throw new JwtAuthException("Invalid token type");
        }
        return claims;
    }

    public UUID extractUserIdFromRefreshToken(String token) {
        return UUID.fromString(validateRefreshToken(token).getSubject());
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Claims parseAndVerify(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(props.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.debug("[JWT] Token expired: {}", ex.getMessage());
            throw new JwtAuthException("Token has expired");
        } catch (SignatureException ex) {
            log.warn("[JWT] Signature invalid — possible tampering: {}", ex.getMessage());
            throw new JwtAuthException("Token signature is invalid");
        } catch (MalformedJwtException ex) {
            log.warn("[JWT] Malformed token: {}", ex.getMessage());
            throw new JwtAuthException("Token is malformed");
        } catch (JwtException ex) {
            log.warn("[JWT] JWT error: {}", ex.getMessage());
            throw new JwtAuthException("Token validation failed");
        }
    }

    // ── Inner exception class ──────────────────────────────────────────────

    /** Thrown on any JWT validation failure. Carries a safe client-facing message. */
    public static class JwtAuthException extends RuntimeException {
        public JwtAuthException(String message) {
            super(message);
        }
    }
}
