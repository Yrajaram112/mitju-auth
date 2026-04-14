package com.mitju.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted refresh token row — one row per active user session.
 * Maps to auth.refresh_tokens.
 *
 * We store SHA-256(rawToken) — NEVER the raw token itself.
 * Revocation: set revoked_at. Cleanup: scheduled job deletes expired rows.
 */
@Entity
@Table(name = "refresh_tokens", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** FK to auth.users.id — CASCADE DELETE handled by DB. */
    @Column(nullable = false, updatable = false)
    private UUID userId;

    /** SHA-256 hex digest of the raw refresh token string. */
    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String tokenHash;

    /**
     * Device/browser info for the session display ("Logged in from Chrome on macOS").
     * Stored as JSONB: {os, browser, deviceId, userAgent}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> deviceInfo;

    /** IP address of the client that created this token. */
    @Column(columnDefinition = "inet")
    private String ipAddress;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Non-null = revoked. We keep rows after revocation for audit trails. */
    private Instant revokedAt;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // ── Helpers ────────────────────────────────────────────────────────────

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }
}
