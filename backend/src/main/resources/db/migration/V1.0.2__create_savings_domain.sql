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
