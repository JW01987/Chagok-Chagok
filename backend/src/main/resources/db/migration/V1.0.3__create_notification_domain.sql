-- ============================================================
-- V1.0.3 Account + Notification + Report 도메인
-- ============================================================

-- brokerage_accounts (Beta용 — 구조만 생성)
CREATE TABLE brokerage_accounts (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT       NOT NULL REFERENCES users(id),
    brokerage                 VARCHAR(20)  NOT NULL,
    account_number_encrypted  VARCHAR(255) NOT NULL,
    account_alias             VARCHAR(50),
    access_token_encrypted    TEXT,
    token_expires_at          TIMESTAMPTZ,
    cash_balance              BIGINT       NOT NULL DEFAULT 0,
    balance_synced_at         TIMESTAMPTZ,
    is_primary                BOOLEAN      NOT NULL DEFAULT FALSE,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'CONNECTED',
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_brokerage CHECK (brokerage IN ('KIS', 'KIWOOM')),
    CONSTRAINT chk_account_status CHECK (status IN ('CONNECTED', 'DISCONNECTED', 'ERROR'))
);

-- notification_settings
CREATE TABLE notification_settings (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT      NOT NULL REFERENCES users(id),
    savings_reminder            BOOLEAN     NOT NULL DEFAULT TRUE,
    savings_reminder_days_before SMALLINT   NOT NULL DEFAULT 3,
    savings_completed           BOOLEAN     NOT NULL DEFAULT TRUE,
    insufficient_balance        BOOLEAN     NOT NULL DEFAULT TRUE,
    weekly_report               BOOLEAN     NOT NULL DEFAULT TRUE,
    rebalancing_alert           BOOLEAN     NOT NULL DEFAULT TRUE,
    goal_milestone              BOOLEAN     NOT NULL DEFAULT TRUE,
    market_alert                BOOLEAN     NOT NULL DEFAULT FALSE,
    channel_push                BOOLEAN     NOT NULL DEFAULT TRUE,
    channel_kakao               BOOLEAN     NOT NULL DEFAULT FALSE,
    channel_email               BOOLEAN     NOT NULL DEFAULT FALSE,
    quiet_start_time            TIME,
    quiet_end_time              TIME,
    fcm_token                   VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notification_settings_user UNIQUE (user_id)
);

-- notification_logs
CREATE TABLE notification_logs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users(id),
    type           VARCHAR(50) NOT NULL,
    channel        VARCHAR(20) NOT NULL,
    title          VARCHAR(255) NOT NULL,
    body           TEXT        NOT NULL,
    reference_id   BIGINT,
    reference_type VARCHAR(50),
    status         VARCHAR(20) NOT NULL DEFAULT 'SENT',
    sent_at        TIMESTAMPTZ,
    fail_reason    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notification_type CHECK (type IN (
        'SAVINGS_REMINDER','SAVINGS_COMPLETED','INSUFFICIENT_BALANCE',
        'WEEKLY_REPORT','REBALANCING','GOAL_MILESTONE','MARKET_ALERT'
    )),
    CONSTRAINT chk_notification_channel CHECK (channel IN ('PUSH', 'KAKAO', 'EMAIL')),
    CONSTRAINT chk_notification_status CHECK (status IN ('SENT', 'FAILED', 'SKIPPED'))
);

-- monthly_reports (Beta용)
CREATE TABLE monthly_reports (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users(id),
    year_month            CHAR(7)      NOT NULL,
    total_invested        BIGINT       NOT NULL,
    total_valuation       BIGINT       NOT NULL,
    monthly_return_rate   DECIMAL(6,3) NOT NULL,
    cumulative_invested   BIGINT       NOT NULL,
    cumulative_valuation  BIGINT       NOT NULL,
    cumulative_return_rate DECIMAL(8,3) NOT NULL,
    goal_achievement_rate DECIMAL(5,2),
    peer_avg_return_rate  DECIMAL(6,3),
    report_image_url      VARCHAR(500),
    sent_at               TIMESTAMPTZ,
    status                VARCHAR(20)  NOT NULL DEFAULT 'GENERATED',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_monthly_report UNIQUE (user_id, year_month),
    CONSTRAINT chk_report_status CHECK (status IN ('GENERATED', 'SENT', 'FAILED'))
);

-- 인덱스
CREATE INDEX idx_notification_logs_user ON notification_logs(user_id, created_at DESC);
CREATE INDEX idx_monthly_reports_user ON monthly_reports(user_id, year_month DESC);
