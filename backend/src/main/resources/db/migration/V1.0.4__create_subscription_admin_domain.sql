-- ============================================================
-- V1.0.4 Subscription + Admin 도메인
-- ============================================================

-- subscription_plans
CREATE TABLE subscription_plans (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(30) NOT NULL,
    name           VARCHAR(50) NOT NULL,
    price_monthly  INTEGER     NOT NULL DEFAULT 0,
    description    TEXT,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_plan_code UNIQUE (code),
    CONSTRAINT chk_price_non_negative CHECK (price_monthly >= 0)
);

-- subscription_plan_features
CREATE TABLE subscription_plan_features (
    id                   BIGSERIAL PRIMARY KEY,
    subscription_plan_id BIGINT       NOT NULL REFERENCES subscription_plans(id),
    feature_key          VARCHAR(100) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_plan_feature UNIQUE (subscription_plan_id, feature_key)
);

-- user_subscriptions
CREATE TABLE user_subscriptions (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES users(id),
    subscription_plan_id BIGINT      NOT NULL REFERENCES subscription_plans(id),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at           TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    payment_method       VARCHAR(50),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_subscription UNIQUE (user_id),
    CONSTRAINT chk_subscription_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED'))
);

-- admin_users
CREATE TABLE admin_users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    role          VARCHAR(30)  NOT NULL DEFAULT 'CONTENT_MANAGER',
    last_login_at TIMESTAMPTZ,
    last_login_ip VARCHAR(45),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_admin_email UNIQUE (email),
    CONSTRAINT chk_admin_role CHECK (role IN ('SUPER_ADMIN', 'CONTENT_MANAGER'))
);
