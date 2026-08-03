-- ============================================================
-- V1.0.0 User 도메인
-- ============================================================

-- users
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255),
    nickname        VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    login_fail_count SMALLINT    NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'WITHDRAWN'))
);

-- user_oauth
CREATE TABLE user_oauth (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES users(id),
    provider                VARCHAR(20) NOT NULL,
    provider_user_id        VARCHAR(255) NOT NULL,
    access_token_encrypted  TEXT,
    refresh_token_encrypted TEXT,
    token_expires_at        TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_oauth_provider UNIQUE (provider, provider_user_id),
    CONSTRAINT chk_user_oauth_provider CHECK (provider IN ('KAKAO', 'APPLE'))
);

-- refresh_tokens
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(255) NOT NULL,
    device_info VARCHAR(255),
    ip_address  VARCHAR(45),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

-- user_onboarding
CREATE TABLE user_onboarding (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT     NOT NULL REFERENCES users(id),
    investment_type         VARCHAR(20) NOT NULL,
    monthly_amount          INTEGER    NOT NULL,
    goal_type               VARCHAR(20) NOT NULL,
    goal_amount             INTEGER,
    goal_period_months      SMALLINT,
    salary_day              SMALLINT,
    onboarding_completed_at TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_onboarding_user UNIQUE (user_id),
    CONSTRAINT chk_investment_type CHECK (investment_type IN ('SAFE', 'BALANCED', 'GROWTH', 'KOREA_FOCUSED')),
    CONSTRAINT chk_goal_type CHECK (goal_type IN ('LUMP_SUM', 'RETIREMENT', 'FREE')),
    CONSTRAINT chk_monthly_amount CHECK (monthly_amount BETWEEN 50000 AND 1000000),
    CONSTRAINT chk_salary_day CHECK (salary_day BETWEEN 1 AND 28)
);

-- 인덱스
CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status ON users(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;
