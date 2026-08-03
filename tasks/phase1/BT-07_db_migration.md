# BT-07 | DB 설계 및 마이그레이션

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ✅ 완료 (PR #28, `feature/bt-07-auto` → `main`)
- **선행 태스크**: BT-04 (Docker Compose)
- **완료 기준**: 22개 테이블 Flyway 마이그레이션 완료 + 시드 데이터 투입 + 인덱스 확인

---

## 개요

도메인 엔티티 설계서 기반으로 Flyway SQL 마이그레이션을 작성한다.
버전 순서: `V1.0.0__` (User) → `V1.0.1__` (Portfolio) → ... → `V1.0.6__` (Subscription/Admin)
시드 데이터는 Repeatable 마이그레이션 `R__` 파일로 관리한다.

---

## BT-07-01 | ERD 파일 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `docs/ERD.dbml`

```dbml
// 차곡차곡 ERD — dbdiagram.io 에서 렌더링
// https://dbdiagram.io

Table users {
  id bigserial [pk]
  email varchar(255) [unique, not null]
  password_hash varchar(255)
  nickname varchar(50) [not null]
  status varchar(20) [not null, note: 'ACTIVE | LOCKED | WITHDRAWN']
  login_fail_count smallint [default: 0]
  locked_until timestamp
  last_login_at timestamp
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
  deleted_at timestamptz
}

Table user_oauth {
  id bigserial [pk]
  user_id bigint [ref: > users.id, not null]
  provider varchar(20) [not null, note: 'KAKAO | APPLE']
  provider_user_id varchar(255) [not null]
  access_token_encrypted text
  refresh_token_encrypted text
  token_expires_at timestamp
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
  indexes { (provider, provider_user_id) [unique] }
}

Table refresh_tokens {
  id bigserial [pk]
  user_id bigint [ref: > users.id, not null]
  token_hash varchar(255) [unique, not null]
  device_info varchar(255)
  ip_address varchar(45)
  expires_at timestamp [not null]
  revoked_at timestamp
  created_at timestamptz [not null]
}

Table user_onboarding {
  id bigserial [pk]
  user_id bigint [ref: - users.id, unique, not null]
  investment_type varchar(20) [not null, note: 'SAFE | BALANCED | GROWTH | KOREA_FOCUSED']
  monthly_amount int [not null]
  goal_type varchar(20) [not null, note: 'LUMP_SUM | RETIREMENT | FREE']
  goal_amount int
  goal_period_months smallint
  salary_day smallint
  onboarding_completed_at timestamp
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Table portfolio_templates {
  id bigserial [pk]
  code varchar(50) [unique, not null]
  name varchar(100) [not null]
  description text [not null]
  investment_type varchar(20) [not null]
  expected_return_min decimal(4,2) [not null]
  expected_return_max decimal(4,2) [not null]
  mdd decimal(5,2)
  volatility decimal(5,2)
  rebalance_threshold decimal(4,2) [default: 5.00]
  is_active boolean [default: true]
  display_order smallint [default: 0]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Table etfs {
  id bigserial [pk]
  ticker varchar(20) [unique, not null]
  name varchar(100) [not null]
  exchange varchar(10) [not null, note: 'KRX | NYSE | NASDAQ']
  asset_class varchar(20) [not null, note: 'STOCK | BOND | GOLD | COMMODITY | REAL_ESTATE']
  currency char(3) [default: 'KRW']
  management_company varchar(100)
  expense_ratio decimal(4,3)
  is_active boolean [default: true]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Table portfolio_allocations {
  id bigserial [pk]
  portfolio_template_id bigint [ref: > portfolio_templates.id, not null]
  etf_id bigint [ref: > etfs.id, not null]
  target_ratio decimal(5,2) [not null]
  display_order smallint [default: 0]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
  indexes { (portfolio_template_id, etf_id) [unique] }
}

Table backtest_returns {
  id bigserial [pk]
  portfolio_template_id bigint [ref: > portfolio_templates.id, not null]
  year_month char(7) [not null]
  return_rate decimal(6,3) [not null]
  cumulative_return_rate decimal(8,3)
  created_at timestamptz [not null]
  indexes { (portfolio_template_id, year_month) [unique] }
}

Table user_portfolios {
  id bigserial [pk]
  user_id bigint [ref: > users.id, not null]
  portfolio_template_id bigint [ref: > portfolio_templates.id, not null]
  status varchar(20) [not null, note: 'ACTIVE | PAUSED | TERMINATED']
  selected_at timestamp [not null]
  terminated_at timestamp
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Table savings_plans {
  id bigserial [pk]
  user_id bigint [ref: > users.id, not null]
  user_portfolio_id bigint [ref: > user_portfolios.id, not null]
  status varchar(20) [not null, note: 'ACTIVE | PAUSED | TERMINATED']
  amount int [not null]
  amount_type varchar(20) [not null, note: 'FIXED | SALARY_RATIO']
  salary_ratio decimal(4,2)
  frequency varchar(20) [not null, note: 'MONTHLY | BIMONTHLY | WEEKLY']
  day_of_month smallint
  day_of_week smallint
  purchase_method varchar(20) [not null, note: 'LUMP_SUM | DCA']
  dca_count smallint [default: 4]
  pause_reason varchar(255)
  pause_until timestamp
  next_schedule_at timestamp
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
  indexes { (status, next_schedule_at) }
}

Table savings_executions {
  id bigserial [pk]
  savings_plan_id bigint [ref: > savings_plans.id, not null]
  scheduled_at timestamp [not null]
  executed_at timestamp
  status varchar(20) [not null, note: 'PENDING | IN_PROGRESS | SUCCESS | FAILED | SKIPPED']
  total_amount int [not null]
  fail_reason text
  retry_count smallint [default: 0]
  last_retried_at timestamp
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
  indexes { (savings_plan_id, scheduled_at) [unique] }
}

Table savings_execution_items {
  id bigserial [pk]
  savings_execution_id bigint [ref: > savings_executions.id, not null]
  etf_id bigint [ref: > etfs.id, not null]
  sequence smallint [not null]
  scheduled_buy_at timestamp [not null]
  executed_buy_at timestamp
  amount int [not null]
  quantity decimal(12,4)
  buy_price decimal(12,2)
  status varchar(20) [not null, note: 'PENDING | SUCCESS | FAILED']
  order_id varchar(100)
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Table notification_settings {
  id bigserial [pk]
  user_id bigint [ref: - users.id, unique, not null]
  savings_reminder boolean [default: true]
  savings_reminder_days_before smallint [default: 3]
  savings_completed boolean [default: true]
  insufficient_balance boolean [default: true]
  weekly_report boolean [default: true]
  rebalancing_alert boolean [default: true]
  goal_milestone boolean [default: true]
  market_alert boolean [default: false]
  channel_push boolean [default: true]
  channel_kakao boolean [default: false]
  channel_email boolean [default: false]
  quiet_start_time time
  quiet_end_time time
  fcm_token varchar(255)
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Table subscription_plans {
  id bigserial [pk]
  code varchar(30) [unique, not null]
  name varchar(50) [not null]
  price_monthly int [not null]
  description text
  is_active boolean [default: true]
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}

Table user_subscriptions {
  id bigserial [pk]
  user_id bigint [ref: - users.id, unique, not null]
  subscription_plan_id bigint [ref: > subscription_plans.id, not null]
  status varchar(20) [not null, note: 'ACTIVE | EXPIRED | CANCELLED']
  started_at timestamp [not null]
  expires_at timestamp
  cancelled_at timestamp
  payment_method varchar(50)
  created_at timestamptz [not null]
  updated_at timestamptz [not null]
}
```

### 완료 확인
- [ ] dbdiagram.io에서 ERD 렌더링 확인

---

## BT-07-02 | Flyway V1.0.0 — User 도메인

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `backend/src/main/resources/db/migration/V1.0.0__create_user_domain.sql`

```sql
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
```

---

## BT-07-03 | Flyway V1.0.1 — Portfolio 도메인

**작업 유형**: 파일 생성

### 생성 파일: `db/migration/V1.0.1__create_portfolio_domain.sql`

```sql
-- ============================================================
-- V1.0.1 Portfolio 도메인 (마스터)
-- ============================================================

-- portfolio_templates
CREATE TABLE portfolio_templates (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(50)    NOT NULL,
    name                 VARCHAR(100)   NOT NULL,
    description          TEXT           NOT NULL,
    investment_type      VARCHAR(20)    NOT NULL,
    expected_return_min  DECIMAL(4,2)   NOT NULL,
    expected_return_max  DECIMAL(4,2)   NOT NULL,
    mdd                  DECIMAL(5,2),
    volatility           DECIMAL(5,2),
    rebalance_threshold  DECIMAL(4,2)   NOT NULL DEFAULT 5.00,
    is_active            BOOLEAN        NOT NULL DEFAULT TRUE,
    display_order        SMALLINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_portfolio_code UNIQUE (code),
    CONSTRAINT chk_portfolio_investment_type CHECK (investment_type IN ('SAFE', 'BALANCED', 'GROWTH', 'KOREA_FOCUSED'))
);

-- etfs
CREATE TABLE etfs (
    id                  BIGSERIAL PRIMARY KEY,
    ticker              VARCHAR(20)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    exchange            VARCHAR(10)  NOT NULL,
    asset_class         VARCHAR(20)  NOT NULL,
    currency            CHAR(3)      NOT NULL DEFAULT 'KRW',
    management_company  VARCHAR(100),
    expense_ratio       DECIMAL(4,3),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_etf_ticker UNIQUE (ticker),
    CONSTRAINT chk_etf_exchange CHECK (exchange IN ('KRX', 'NYSE', 'NASDAQ')),
    CONSTRAINT chk_etf_asset_class CHECK (asset_class IN ('STOCK', 'BOND', 'GOLD', 'COMMODITY', 'REAL_ESTATE'))
);

-- portfolio_allocations
CREATE TABLE portfolio_allocations (
    id                    BIGSERIAL PRIMARY KEY,
    portfolio_template_id BIGINT       NOT NULL REFERENCES portfolio_templates(id),
    etf_id                BIGINT       NOT NULL REFERENCES etfs(id),
    target_ratio          DECIMAL(5,2) NOT NULL,
    display_order         SMALLINT     NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_portfolio_allocation UNIQUE (portfolio_template_id, etf_id),
    CONSTRAINT chk_target_ratio CHECK (target_ratio > 0 AND target_ratio <= 100)
);

-- backtest_returns
CREATE TABLE backtest_returns (
    id                    BIGSERIAL PRIMARY KEY,
    portfolio_template_id BIGINT      NOT NULL REFERENCES portfolio_templates(id),
    year_month            CHAR(7)     NOT NULL,
    return_rate           DECIMAL(6,3) NOT NULL,
    cumulative_return_rate DECIMAL(8,3),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_backtest_year_month UNIQUE (portfolio_template_id, year_month)
);

-- etf_prices (Beta용 — 구조만 미리 생성)
CREATE TABLE etf_prices (
    id          BIGSERIAL PRIMARY KEY,
    etf_id      BIGINT       NOT NULL REFERENCES etfs(id),
    trade_date  DATE         NOT NULL,
    close_price DECIMAL(12,2) NOT NULL,
    open_price  DECIMAL(12,2),
    high_price  DECIMAL(12,2),
    low_price   DECIMAL(12,2),
    volume      BIGINT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_etf_price_date UNIQUE (etf_id, trade_date)
);

-- 인덱스
CREATE INDEX idx_portfolio_templates_active ON portfolio_templates(is_active, display_order);
CREATE INDEX idx_etf_prices_date ON etf_prices(trade_date DESC);
```

---

## BT-07-04 | Flyway V1.0.2 — UserPortfolio + Savings 도메인

**작업 유형**: 파일 생성

### 생성 파일: `db/migration/V1.0.2__create_savings_domain.sql`

```sql
-- ============================================================
-- V1.0.2 UserPortfolio + Savings 도메인
-- ============================================================

-- user_portfolios
CREATE TABLE user_portfolios (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users(id),
    portfolio_template_id BIGINT      NOT NULL REFERENCES portfolio_templates(id),
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    selected_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    terminated_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_portfolio_status CHECK (status IN ('ACTIVE', 'PAUSED', 'TERMINATED'))
);

-- user_asset_holdings (Beta용)
CREATE TABLE user_asset_holdings (
    id                 BIGSERIAL PRIMARY KEY,
    user_portfolio_id  BIGINT       NOT NULL REFERENCES user_portfolios(id),
    etf_id             BIGINT       NOT NULL REFERENCES etfs(id),
    quantity           DECIMAL(12,4) NOT NULL,
    average_buy_price  DECIMAL(12,2) NOT NULL,
    current_price      DECIMAL(12,2),
    current_ratio      DECIMAL(5,2),
    synced_at          TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_holding UNIQUE (user_portfolio_id, etf_id)
);

-- savings_plans
CREATE TABLE savings_plans (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id),
    user_portfolio_id BIGINT      NOT NULL REFERENCES user_portfolios(id),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    amount            INTEGER     NOT NULL,
    amount_type       VARCHAR(20) NOT NULL DEFAULT 'FIXED',
    salary_ratio      DECIMAL(4,2),
    frequency         VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    day_of_month      SMALLINT,
    day_of_week       SMALLINT,
    purchase_method   VARCHAR(20) NOT NULL DEFAULT 'DCA',
    dca_count         SMALLINT    NOT NULL DEFAULT 4,
    pause_reason      VARCHAR(255),
    pause_until       TIMESTAMPTZ,
    next_schedule_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_savings_status CHECK (status IN ('ACTIVE', 'PAUSED', 'TERMINATED')),
    CONSTRAINT chk_amount_type CHECK (amount_type IN ('FIXED', 'SALARY_RATIO')),
    CONSTRAINT chk_frequency CHECK (frequency IN ('MONTHLY', 'BIMONTHLY', 'WEEKLY')),
    CONSTRAINT chk_purchase_method CHECK (purchase_method IN ('LUMP_SUM', 'DCA')),
    CONSTRAINT chk_day_of_month CHECK (day_of_month BETWEEN 1 AND 28)
);

-- savings_executions
CREATE TABLE savings_executions (
    id              BIGSERIAL PRIMARY KEY,
    savings_plan_id BIGINT      NOT NULL REFERENCES savings_plans(id),
    scheduled_at    TIMESTAMPTZ NOT NULL,
    executed_at     TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount    INTEGER     NOT NULL,
    fail_reason     TEXT,
    retry_count     SMALLINT    NOT NULL DEFAULT 0,
    last_retried_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_execution_schedule UNIQUE (savings_plan_id, scheduled_at),
    CONSTRAINT chk_execution_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED', 'SKIPPED'))
);

-- savings_execution_items
CREATE TABLE savings_execution_items (
    id                   BIGSERIAL PRIMARY KEY,
    savings_execution_id BIGINT       NOT NULL REFERENCES savings_executions(id),
    etf_id               BIGINT       NOT NULL REFERENCES etfs(id),
    sequence             SMALLINT     NOT NULL,
    scheduled_buy_at     TIMESTAMPTZ  NOT NULL,
    executed_buy_at      TIMESTAMPTZ,
    amount               INTEGER      NOT NULL,
    quantity             DECIMAL(12,4),
    buy_price            DECIMAL(12,2),
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    order_id             VARCHAR(100),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_item_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

-- 인덱스 (배치 조회 핵심)
CREATE INDEX idx_user_portfolios_user ON user_portfolios(user_id);
CREATE INDEX idx_savings_plans_batch ON savings_plans(status, next_schedule_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_savings_executions_plan ON savings_executions(savings_plan_id, scheduled_at);
CREATE INDEX idx_savings_execution_items_exec ON savings_execution_items(savings_execution_id);
```

---

## BT-07-05 | Flyway V1.0.3 — Account / Notification / Report 도메인

**작업 유형**: 파일 생성

### 생성 파일: `db/migration/V1.0.3__create_notification_domain.sql`

```sql
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
```

---

## BT-07-06 | Flyway V1.0.4 — Subscription / Admin 도메인

**작업 유형**: 파일 생성

### 생성 파일: `db/migration/V1.0.4__create_subscription_admin_domain.sql`

```sql
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
```

---

## BT-07-07 ~ BT-07-11 | 시드 데이터 (Repeatable 마이그레이션)

**작업 유형**: 파일 생성

### 생성 파일: `db/migration/R__01_portfolio_seed.sql`

```sql
-- ============================================================
-- R__01 포트폴리오 마스터 시드 데이터
-- ============================================================

TRUNCATE portfolio_templates, etfs, portfolio_allocations, backtest_returns RESTART IDENTITY CASCADE;

-- 포트폴리오 4종
INSERT INTO portfolio_templates (code, name, description, investment_type, expected_return_min, expected_return_max, mdd, volatility, display_order) VALUES
('ALL_WEATHER',    '올웨더 포트폴리오',  '레이 달리오의 전천후 포트폴리오. 주식·채권·금·원자재 분산으로 어떤 경제 환경에서도 안정적인 수익을 추구합니다.', 'SAFE',          7.00, 9.00,  -12.00, 7.50, 1),
('MIXED',          '주식-채권 혼합',     '주식 60% + 채권 40%. 적절한 위험으로 장기 수익을 추구하는 균형잡힌 포트폴리오입니다.',                           'BALANCED',      8.00, 10.00, -20.00, 11.00, 2),
('GLOBAL_GROWTH',  '글로벌 성장형',      '국내·미국·신흥국 ETF로 글로벌 성장에 투자합니다. 높은 수익을 기대하지만 변동성도 큽니다.',                       'GROWTH',        10.00, 12.00, -30.00, 16.00, 3),
('KOREA_FOCUSED',  '한국 집중형',        '코스피200 ETF + 국내 채권. 해외 투자가 부담스럽다면 국내 자산으로 시작하세요.',                                 'KOREA_FOCUSED', 6.00,  8.00,  -18.00, 10.00, 4);

-- ETF 마스터
INSERT INTO etfs (ticker, name, exchange, asset_class, currency, management_company, expense_ratio) VALUES
-- 국내 주식
('KODEX200',        'KODEX 200',                    'KRX',    'STOCK',     'KRW', '삼성자산운용', 0.150),
('TIGER200',        'TIGER 200',                    'KRX',    'STOCK',     'KRW', '미래에셋자산운용', 0.050),
-- 미국 주식
('ACE_SPX',         'ACE 미국S&P500',               'KRX',    'STOCK',     'KRW', '한국투자신탁운용', 0.070),
('KODEX_NASDAQ100', 'KODEX 미국나스닥100',            'KRX',    'STOCK',     'KRW', '삼성자산운용', 0.050),
-- 신흥국
('TIGER_EM',        'TIGER 신흥국 ETF',              'KRX',    'STOCK',     'KRW', '미래에셋자산운용', 0.250),
-- 국내 채권
('KODEX_BOND',      'KODEX 국고채3년',               'KRX',    'BOND',      'KRW', '삼성자산운용', 0.150),
('TIGER_BOND10',    'TIGER 국고채10년',              'KRX',    'BOND',      'KRW', '미래에셋자산운용', 0.150),
-- 미국 장기채
('ACE_US_BOND30',   'ACE 미국30년국채액티브',         'KRX',    'BOND',      'KRW', '한국투자신탁운용', 0.050),
-- 미국 중기채
('TIGER_US_BOND7',  'TIGER 미국채7년',               'KRX',    'BOND',      'KRW', '미래에셋자산운용', 0.090),
-- 금
('ACE_GOLD',        'ACE KRX금현물',                 'KRX',    'GOLD',      'KRW', '한국투자신탁운용', 0.500),
-- 원자재
('TIGER_COMMODITY', 'TIGER 원자재선물Enhanced',       'KRX',    'COMMODITY', 'KRW', '미래에셋자산운용', 0.390);

-- 포트폴리오 구성 비율
-- ALL_WEATHER: 주식30 / 장기채40 / 중기채15 / 금7.5 / 원자재7.5
INSERT INTO portfolio_allocations (portfolio_template_id, etf_id, target_ratio, display_order)
SELECT pt.id, e.id, v.ratio, v.ord FROM portfolio_templates pt
CROSS JOIN (VALUES
    ('KODEX200', 15.00, 1), ('ACE_SPX', 15.00, 2),
    ('ACE_US_BOND30', 40.00, 3), ('TIGER_US_BOND7', 15.00, 4),
    ('ACE_GOLD', 7.50, 5), ('TIGER_COMMODITY', 7.50, 6)
) AS v(ticker, ratio, ord)
JOIN etfs e ON e.ticker = v.ticker
WHERE pt.code = 'ALL_WEATHER';

-- MIXED: 주식60 / 채권40
INSERT INTO portfolio_allocations (portfolio_template_id, etf_id, target_ratio, display_order)
SELECT pt.id, e.id, v.ratio, v.ord FROM portfolio_templates pt
CROSS JOIN (VALUES
    ('KODEX200', 30.00, 1), ('ACE_SPX', 30.00, 2),
    ('KODEX_BOND', 20.00, 3), ('TIGER_BOND10', 20.00, 4)
) AS v(ticker, ratio, ord)
JOIN etfs e ON e.ticker = v.ticker
WHERE pt.code = 'MIXED';

-- GLOBAL_GROWTH: 국내30 / 미국50 / 신흥국20
INSERT INTO portfolio_allocations (portfolio_template_id, etf_id, target_ratio, display_order)
SELECT pt.id, e.id, v.ratio, v.ord FROM portfolio_templates pt
CROSS JOIN (VALUES
    ('KODEX200', 30.00, 1), ('ACE_SPX', 30.00, 2),
    ('KODEX_NASDAQ100', 20.00, 3), ('TIGER_EM', 20.00, 4)
) AS v(ticker, ratio, ord)
JOIN etfs e ON e.ticker = v.ticker
WHERE pt.code = 'GLOBAL_GROWTH';

-- KOREA_FOCUSED: 코스피70 / 국내채권30
INSERT INTO portfolio_allocations (portfolio_template_id, etf_id, target_ratio, display_order)
SELECT pt.id, e.id, v.ratio, v.ord FROM portfolio_templates pt
CROSS JOIN (VALUES
    ('KODEX200', 50.00, 1), ('TIGER200', 20.00, 2),
    ('KODEX_BOND', 15.00, 3), ('TIGER_BOND10', 15.00, 4)
) AS v(ticker, ratio, ord)
JOIN etfs e ON e.ticker = v.ticker
WHERE pt.code = 'KOREA_FOCUSED';
```

### 생성 파일: `db/migration/R__02_subscription_seed.sql`

```sql
-- ============================================================
-- R__02 구독 플랜 시드 데이터
-- ============================================================

TRUNCATE subscription_plans, subscription_plan_features RESTART IDENTITY CASCADE;

INSERT INTO subscription_plans (code, name, price_monthly, description) VALUES
('FREE',          '무료',       0,    '포트폴리오 추천, 시뮬레이션, 대시보드 기본 기능'),
('PREMIUM',       '프리미엄',   2900, '자동 적립 실행, 월간 리포트, 리밸런싱 알림, 무제한 시나리오'),
('PREMIUM_PLUS',  '프리미엄+',  4900, '프리미엄 전체 + 세금 리포트, 우선 고객 지원');

INSERT INTO subscription_plan_features (subscription_plan_id, feature_key)
SELECT sp.id, f.key FROM subscription_plans sp
CROSS JOIN (VALUES ('PORTFOLIO_VIEW'), ('SIMULATION'), ('DASHBOARD_BASIC')) AS f(key)
WHERE sp.code = 'FREE';

INSERT INTO subscription_plan_features (subscription_plan_id, feature_key)
SELECT sp.id, f.key FROM subscription_plans sp
CROSS JOIN (VALUES
    ('PORTFOLIO_VIEW'), ('SIMULATION'), ('DASHBOARD_BASIC'),
    ('AUTO_SAVINGS'), ('MONTHLY_REPORT'), ('REBALANCING_ALERT'), ('UNLIMITED_SIMULATION')
) AS f(key)
WHERE sp.code = 'PREMIUM';

INSERT INTO subscription_plan_features (subscription_plan_id, feature_key)
SELECT sp.id, f.key FROM subscription_plans sp
CROSS JOIN (VALUES
    ('PORTFOLIO_VIEW'), ('SIMULATION'), ('DASHBOARD_BASIC'),
    ('AUTO_SAVINGS'), ('MONTHLY_REPORT'), ('REBALANCING_ALERT'), ('UNLIMITED_SIMULATION'),
    ('TAX_REPORT'), ('PRIORITY_SUPPORT')
) AS f(key)
WHERE sp.code = 'PREMIUM_PLUS';
```

---

## BT-07-12 | 인덱스 및 마이그레이션 최종 확인

**작업 유형**: 검증

```bash
# Docker Compose 실행 후 마이그레이션 확인
docker-compose up -d postgres
sleep 5

# 마이그레이션 상태 확인 (Flyway)
docker exec chagok-backend ./gradlew flywayInfo

# 테이블 목록 확인
docker exec chagok-postgres psql -U chagok -d chagok_db -c "\dt"

# 시드 데이터 확인
docker exec chagok-postgres psql -U chagok -d chagok_db \
  -c "SELECT code, name, expected_return_min, expected_return_max FROM portfolio_templates;"
```

### 완료 확인
- [ ] 22개 테이블 모두 생성
- [ ] 포트폴리오 4종 시드 데이터 투입
- [ ] 구독 플랜 3종 시드 데이터 투입
- [ ] 핵심 인덱스 생성 확인 (`idx_savings_plans_batch` 등)

---

## 완료 체크리스트

- [ ] BT-07-01: ERD.dbml 작성
- [ ] BT-07-02: V1.0.0 User 도메인 마이그레이션
- [ ] BT-07-03: V1.0.1 Portfolio 도메인 마이그레이션
- [ ] BT-07-04: V1.0.2 Savings 도메인 마이그레이션
- [ ] BT-07-05: V1.0.3 Notification/Report 도메인 마이그레이션
- [ ] BT-07-06: V1.0.4 Subscription/Admin 도메인 마이그레이션
- [ ] BT-07-08~11: 시드 데이터 (포트폴리오, ETF, 구독 플랜)
- [ ] BT-07-12: `./gradlew flywayInfo` 전체 통과

**다음 태스크**: BT-08 (Auth)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
