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
