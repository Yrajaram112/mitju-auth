package com.mitju.authservice.repository;

import com.mitju.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    /** Count active (non-revoked, non-expired) sessions for a user. */
    @Query("SELECT COUNT(r) FROM RefreshToken r " +
           "WHERE r.userId = :userId AND r.revokedAt IS NULL AND r.expiresAt > :now")
    long countActiveSessions(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Revoke all sessions for a user — used on password change / account ban. */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now " +
           "WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Cleanup job: delete rows that expired more than 7 days ago. */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
