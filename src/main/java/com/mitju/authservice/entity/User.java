package com.mitju.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a Mitju system user.
 * Maps to auth.users in PostgreSQL.
 * Schema is owned by Flyway — never set ddl-auto=update/create.
 */
@Entity
@Table(name = "users", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "passwordHash")   // never log the hash
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 20)
    private String phoneNumber;

    @Column(length = 5)
    private String phoneCountryCode;

    /** BCrypt hash. NULL for social-login users. */
    @Column(columnDefinition = "TEXT")
    private String passwordHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /** Google/Apple subject claim for social login deduplication. */
    @Column(length = 255)
    private String providerId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;

    /** TOTP secret — stored encrypted at the application layer. */
    @Column(columnDefinition = "TEXT")
    private String mfaSecret;

    private Instant lastLoginAt;

    @Column(columnDefinition = "TEXT")
    private String lastLoginIp;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedLoginCount = 0;

    private Instant lockedUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /** Soft delete — set instead of DELETE. */
    private Instant deletedAt;

    // ── Convenience helpers ────────────────────────────────────────────────

    public boolean isActive() {
        return deletedAt == null && accountStatus == AccountStatus.ACTIVE;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isSoftDeleted() {
        return deletedAt != null;
    }

    // ── Enums (must match PostgreSQL ENUM values exactly) ──────────────────

    public enum UserRole {
        USER, FAMILY_MEMBER, MATCHMAKER, ADMIN, SUPER_ADMIN
    }

    public enum AuthProvider {
        LOCAL, GOOGLE, APPLE, FACEBOOK
    }

    public enum AccountStatus {
        PENDING_VERIFICATION, ACTIVE, SUSPENDED, DEACTIVATED, BANNED
    }
}
