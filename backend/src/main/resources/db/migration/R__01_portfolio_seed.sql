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
