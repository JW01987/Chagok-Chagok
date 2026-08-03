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
