package com.mitju.authservice.repository;

import com.mitju.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    /** Find active (non-deleted) user by email. Used in login. */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);

    /** Bulk-reset lock on users whose lockout has expired (scheduled cleanup). */
    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = NULL, u.failedLoginCount = 0 " +
           "WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil < :now")
    int unlockExpiredAccounts(@Param("now") Instant now);
}
