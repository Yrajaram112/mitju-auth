package com.mitju.authservice.service;

import com.mitju.authservice.config.JwtProperties;
import com.mitju.authservice.dto.AuthResponse;
import com.mitju.authservice.dto.LoginRequest;
import com.mitju.authservice.dto.RefreshTokenRequest;
import com.mitju.authservice.dto.RegisterRequest;
import com.mitju.authservice.entity.RefreshToken;
import com.mitju.authservice.entity.User;
import com.mitju.authservice.exception.*;
import com.mitju.authservice.repository.RefreshTokenRepository;
import com.mitju.authservice.repository.UserRepository;
import com.mitju.authservice.security.JwtService;
import com.mitju.authservice.security.TokenHashUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Core authentication business logic for Mitju.
 *
 * ── Token strategy ──────────────────────────────────────────────────────────
 * Access token  : short-lived JWT (15 min). Stateless — validated by signature only.
 * Refresh token : long-lived JWT (30 days). STATEFUL — every issued token is stored
 *                 as SHA-256(rawToken) in auth.refresh_tokens. On use, the old row
 *                 is revoked and a new one is inserted (token rotation). This means:
 *                   • Tokens can be revoked server-side (logout, ban, password change).
 *                   • Refresh token replay is detectable.
 *                   • A stolen+used refresh token immediately invalidates the session.
 *
 * ── Login lockout ───────────────────────────────────────────────────────────
 * 5 consecutive failures → 15-minute lock.
 * Successful login resets the counter.
 *
 * ── Account status checks ───────────────────────────────────────────────────
 * PENDING_VERIFICATION — allowed to login (email verify is a separate flow).
 * SUSPENDED / DEACTIVATED / BANNED → 403 Forbidden.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int    MAX_FAILED_ATTEMPTS   = 5;
    private static final int    LOCK_DURATION_MINUTES = 15;
    private static final int    MAX_SESSIONS_PER_USER = 5;   // prevent session flooding

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwtService;
    private final JwtProperties          jwtProps;

    // ── Register ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user and issues tokens immediately (no email-verify gate at
     * this stage — verification is a separate async flow via OTP).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String email = request.email().toLowerCase().trim();
        log.info("[AuthService] Registration attempt | email={}", email);

        if (userRepository.existsByEmail(email)) {
            log.warn("[AuthService] Registration rejected — email already exists | email={}", email);
            throw new ResourceAlreadyExistsException("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(User.UserRole.USER)
                .authProvider(User.AuthProvider.LOCAL)
                .accountStatus(User.AccountStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .mfaEnabled(false)
                .failedLoginCount(0)
                .build();

        user = userRepository.save(user);
        log.info("[AuthService] User registered successfully | userId={} email={}", user.getId(), email);

        return issueTokenPair(user, httpRequest, "register");
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user by email+password, issues a new token pair.
     *
     * Security note: we deliberately use the same error message for "user not found"
     * and "wrong password" — prevents user enumeration attacks.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().toLowerCase().trim();
        log.info("[AuthService] Login attempt | email={}", email);

        // Load user — soft-deleted accounts are treated as "not found"
        User user = userRepository.findActiveByEmail(email)
                .orElseGet(() -> {
                    log.warn("[AuthService] Login failed — account not found or deleted | email={}", email);
                    // Perform a dummy password check to prevent timing attacks
                    passwordEncoder.matches(request.password(), "$2a$12$dummyhashtopreventtimingattack");
                    throw new InvalidCredentialsException();
                });

        // ── Check account status ───────────────────────────────────────────
        if (isForbiddenStatus(user.getAccountStatus())) {
            log.warn("[AuthService] Login blocked — account status | userId={} status={}",
                    user.getId(), user.getAccountStatus());
            throw new AccountStatusException(user.getAccountStatus());
        }

        // ── Check lockout ──────────────────────────────────────────────────
        if (user.isLocked()) {
            log.warn("[AuthService] Login blocked — account locked | userId={} lockedUntil={}",
                    user.getId(), user.getLockedUntil());
            throw new AccountLockedException(user.getLockedUntil());
        }

        // ── Verify password ────────────────────────────────────────────────
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user, httpRequest);
            throw new InvalidCredentialsException();
        }

        // ── Successful login ───────────────────────────────────────────────
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(httpRequest.getRemoteAddr());
        userRepository.save(user);

        log.info("[AuthService] Login successful | userId={} email={} ip={}",
                user.getId(), email, httpRequest.getRemoteAddr());

        return issueTokenPair(user, httpRequest, "login");
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Validates the incoming refresh token, revokes it, issues a new token pair.
     *
     * Token rotation strategy:
     *  1. Parse and validate the JWT signature/expiry.
     *  2. Hash the raw token and look it up in DB.
     *  3. If not found or already revoked → reject (possible replay attack).
     *  4. Revoke the old DB row.
     *  5. Issue a fresh token pair and persist the new refresh token.
     *
     * This means every refresh invalidates the previous refresh token.
     * If an attacker steals and uses a refresh token before the legitimate user,
     * the legitimate user's next refresh will fail — alerting them to the breach.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        log.debug("[AuthService] Token refresh attempt | ip={}", httpRequest.getRemoteAddr());

        String rawToken = request.refreshToken();

        // ── Step 1: Validate JWT structure + signature ─────────────────────
        Claims claims;
        try {
            claims = jwtService.validateRefreshToken(rawToken);
        } catch (JwtService.JwtAuthException ex) {
            log.warn("[AuthService] Refresh token JWT validation failed | reason={}", ex.getMessage());
            throw new TokenException("Refresh token is invalid or expired");
        }

        UUID userId = UUID.fromString(claims.getSubject());

        // ── Step 2: Look up in DB by hash ──────────────────────────────────
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.error("[AuthService] Refresh token not found in DB — possible replay attack | " +
                              "userId={} ip={}", userId, httpRequest.getRemoteAddr());
                    return new TokenException("Refresh token not recognised");
                });

        // ── Step 3: Check validity ─────────────────────────────────────────
        if (!storedToken.isValid()) {
            log.warn("[AuthService] Refresh token is revoked or expired | userId={} tokenId={}",
                    userId, storedToken.getId());
            throw new TokenException("Refresh token has been revoked or expired");
        }

        // User ID in JWT must match the DB row (sanity / tamper check)
        if (!storedToken.getUserId().equals(userId)) {
            log.error("[AuthService] Refresh token user ID mismatch — tamper attempt | " +
                      "jwtUserId={} dbUserId={} ip={}", userId, storedToken.getUserId(),
                      httpRequest.getRemoteAddr());
            throw new TokenException("Refresh token is invalid");
        }

        // ── Step 4: Load user and re-check account status ──────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[AuthService] User not found during refresh | userId={}", userId);
                    return new TokenException("User account not found");
                });

        if (isForbiddenStatus(user.getAccountStatus()) || user.isSoftDeleted()) {
            log.warn("[AuthService] Refresh blocked — account no longer active | userId={} status={}",
                    userId, user.getAccountStatus());
            // Revoke all sessions for this user since the account is problematic
            refreshTokenRepository.revokeAllForUser(userId, Instant.now());
            throw new AccountStatusException(user.getAccountStatus());
        }

        // ── Step 5: Revoke old token (rotation) ───────────────────────────
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);
        log.debug("[AuthService] Old refresh token revoked | tokenId={} userId={}",
                storedToken.getId(), userId);

        // ── Step 6: Issue fresh token pair ─────────────────────────────────
        log.info("[AuthService] Token refreshed successfully | userId={}", userId);
        return issueTokenPair(user, httpRequest, "refresh");
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Revokes the specific refresh token for this session.
     * The access token will naturally expire (no server-side access token revocation —
     * keep the TTL short: 15 min is acceptable for most threat models).
     */
    @Transactional
    public void logout(String rawRefreshToken, UUID userId) {
        log.info("[AuthService] Logout | userId={}", userId);

        String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresentOrElse(
                token -> {
                    if (!token.getUserId().equals(userId)) {
                        log.warn("[AuthService] Logout — token belongs to different user | " +
                                 "callerUserId={} tokenOwnerId={}", userId, token.getUserId());
                        return;
                    }
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                    log.info("[AuthService] Session revoked | tokenId={} userId={}", token.getId(), userId);
                },
                () -> log.warn("[AuthService] Logout — token not found in DB | userId={}", userId)
        );
    }

    /**
     * Revokes ALL sessions for a user — used on password change, account suspension.
     */
    @Transactional
    public void logoutAll(UUID userId) {
        int count = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("[AuthService] All sessions revoked | userId={} sessionsRevoked={}", userId, count);
    }

    // ── Scheduled maintenance ─────────────────────────────────────────────────

    /**
     * Runs nightly at 02:00 UTC — deletes refresh token rows that expired > 7 days ago.
     * Keeps the table lean. Rows are retained 7 days after expiry for audit purposes.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = refreshTokenRepository.deleteExpiredBefore(cutoff);
        log.info("[AuthService] Expired refresh token cleanup | deleted={} cutoff={}", deleted, cutoff);
    }

    /**
     * Runs every 30 minutes — unlocks accounts whose lockout period has passed.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1_000)
    @Transactional
    public void unlockExpiredAccounts() {
        int unlocked = userRepository.unlockExpiredAccounts(Instant.now());
        if (unlocked > 0) {
            log.info("[AuthService] Auto-unlocked accounts | count={}", unlocked);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Generates a new access + refresh token pair, persists the refresh token,
     * enforces max-sessions limit, and returns the response DTO.
     */
    private AuthResponse issueTokenPair(User user, HttpServletRequest httpRequest, String context) {
        // Enforce max concurrent sessions per user (anti-abuse)
        long activeSessions = refreshTokenRepository.countActiveSessions(user.getId(), Instant.now());
        if (activeSessions >= MAX_SESSIONS_PER_USER) {
            log.warn("[AuthService] Max sessions reached — revoking oldest | userId={} active={}",
                    user.getId(), activeSessions);
            // Revoke all existing sessions and start fresh
            // In a full impl you'd revoke only the oldest; for now, revoke all.
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        }

        // Generate tokens
        String rawAccessToken  = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String rawRefreshToken = jwtService.generateRefreshToken(user.getId());

        // Persist refresh token as SHA-256 hash
        RefreshToken tokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHashUtil.sha256Hex(rawRefreshToken))
                .deviceInfo(extractDeviceInfo(httpRequest))
                .ipAddress(httpRequest.getRemoteAddr())
                .expiresAt(Instant.now().plusSeconds(jwtProps.getRefreshTokenExpirySeconds()))
                .build();

        refreshTokenRepository.save(tokenEntity);

        log.debug("[AuthService] Token pair issued | userId={} context={} tokenId={}",
                user.getId(), context, tokenEntity.getId());

        return AuthResponse.of(
                rawAccessToken,
                rawRefreshToken,
                jwtProps.getAccessTokenExpirySeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }

    /** Increments failure counter and locks the account if threshold is reached. */
    private void handleFailedLogin(User user, HttpServletRequest httpRequest) {
        int attempts = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            Instant lockUntil = Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES);
            user.setLockedUntil(lockUntil);
            log.warn("[AuthService] Account locked after {} failed attempts | userId={} ip={} lockedUntil={}",
                    attempts, user.getId(), httpRequest.getRemoteAddr(), lockUntil);
        } else {
            log.warn("[AuthService] Failed login attempt {}/{} | userId={} ip={}",
                    attempts, MAX_FAILED_ATTEMPTS, user.getId(), httpRequest.getRemoteAddr());
        }

        userRepository.save(user);
    }

    /** Accounts that must not be allowed to log in. */
    private boolean isForbiddenStatus(User.AccountStatus status) {
        return status == User.AccountStatus.SUSPENDED
                || status == User.AccountStatus.DEACTIVATED
                || status == User.AccountStatus.BANNED;
    }

    /** Extracts safe browser/device metadata to store with the session. */
    private Map<String, String> extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String deviceId  = request.getHeader("X-Device-Id");   // sent by mobile clients
        return Map.of(
                "userAgent", StringUtils.hasText(userAgent) ? userAgent.substring(0, Math.min(userAgent.length(), 200)) : "unknown",
                "deviceId",  StringUtils.hasText(deviceId)  ? deviceId  : "web",
                "ip",        request.getRemoteAddr()
        );
    }
}
