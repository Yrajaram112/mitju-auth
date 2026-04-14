-- =============================================================================
-- Mitju :: Auth Schema
-- V1 — Managed by Flyway. DO NOT edit manually.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS auth;

-- ── ENUM types ────────────────────────────────────────────────────────────────

CREATE TYPE auth.user_role AS ENUM (
    'USER', 'FAMILY_MEMBER', 'MATCHMAKER', 'ADMIN', 'SUPER_ADMIN'
);

CREATE TYPE auth.auth_provider AS ENUM (
    'LOCAL', 'GOOGLE', 'APPLE', 'FACEBOOK'
);

CREATE TYPE auth.account_status AS ENUM (
    'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'BANNED'
);

-- ── users ─────────────────────────────────────────────────────────────────────

CREATE TABLE auth.users (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    email               VARCHAR(255)    NOT NULL UNIQUE,
    phone_number        VARCHAR(20)     UNIQUE,
    phone_country_code  VARCHAR(5),
    password_hash       TEXT,                          -- NULL for social login
    role                auth.user_role          NOT NULL DEFAULT 'USER',
    auth_provider       auth.auth_provider      NOT NULL DEFAULT 'LOCAL',
    provider_id         VARCHAR(255),                  -- Google/Apple sub claim
    account_status      auth.account_status     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    phone_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    mfa_enabled         BOOLEAN         NOT NULL DEFAULT FALSE,
    mfa_secret          TEXT,                          -- TOTP secret (app-layer encrypted)
    last_login_at       TIMESTAMPTZ,
    last_login_ip       TEXT,
    failed_login_count  INT             NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ                    -- soft delete
);

-- ── refresh_tokens ────────────────────────────────────────────────────────────
-- Each row = one active session. Revoke by setting revoked_at.
-- token_hash = SHA-256(actual_token) — we never store raw tokens.

CREATE TABLE auth.refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    device_info JSONB,                                 -- {os, browser, device_id, user_agent}
    ip_address  TEXT,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── otp_codes ────────────────────────────────────────────────────────────────

CREATE TABLE auth.otp_codes (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        REFERENCES auth.users(id) ON DELETE CASCADE,
    email       VARCHAR(255),
    phone       VARCHAR(20),
    code_hash   TEXT        NOT NULL,
    purpose     VARCHAR(50) NOT NULL, -- 'EMAIL_VERIFY','PHONE_VERIFY','PASSWORD_RESET','LOGIN'
    attempts    INT         NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── indexes ──────────────────────────────────────────────────────────────────

CREATE INDEX idx_users_email        ON auth.users(email);
CREATE INDEX idx_users_phone        ON auth.users(phone_number);
CREATE INDEX idx_users_status       ON auth.users(account_status);
CREATE INDEX idx_users_active       ON auth.users(last_login_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_refresh_token_hash ON auth.refresh_tokens(token_hash);
CREATE INDEX idx_refresh_user       ON auth.refresh_tokens(user_id, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX idx_otp_user_purpose   ON auth.otp_codes(user_id, purpose, expires_at)
    WHERE used_at IS NULL;

-- ── updated_at trigger ────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION auth.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at();
