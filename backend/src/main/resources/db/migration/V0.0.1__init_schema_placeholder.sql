-- Phase 0: 스키마 플레이스홀더
-- 실제 테이블은 BT-07 (DB 마이그레이션)에서 V1.0.0__ 이후로 추가

-- Flyway 동작 확인용 더미 테이블
CREATE TABLE IF NOT EXISTS flyway_check (
    id BIGSERIAL PRIMARY KEY,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
