# BT-04 | Docker Compose 로컬 환경

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 0 — 환경 세팅
- **상태**: ⬜ 대기
- **선행 태스크**: BT-02
- **완료 기준**: `docker-compose up` → API + PostgreSQL + Redis 정상 동작 + Swagger 접근 가능

---

## 개요

로컬 개발 환경을 Docker Compose로 통일한다.
PostgreSQL + Redis + Spring Boot 백엔드를 컨테이너로 실행하고,
Flyway 마이그레이션 설정까지 완성한다.

---

## BT-04-01 | docker-compose.yml 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `docker-compose.yml` (루트)

```yaml
version: '3.9'

services:

  # ────────────────────────────────
  # PostgreSQL
  # ────────────────────────────────
  postgres:
    image: postgres:16-alpine
    container_name: chagok-postgres
    environment:
      POSTGRES_DB: chagok_db
      POSTGRES_USER: chagok
      POSTGRES_PASSWORD: chagok1234
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backend/src/main/resources/db/init:/docker-entrypoint-initdb.d  # 초기화 SQL
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chagok -d chagok_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ────────────────────────────────
  # Redis
  # ────────────────────────────────
  redis:
    image: redis:7-alpine
    container_name: chagok-redis
    command: redis-server --requirepass ""
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3

  # ────────────────────────────────
  # Spring Boot 백엔드
  # ────────────────────────────────
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: chagok-backend
    environment:
      SPRING_PROFILES_ACTIVE: local
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: chagok_db
      DB_USERNAME: chagok
      DB_PASSWORD: chagok1234
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JWT_SECRET: local-dev-secret-key-must-be-at-least-256-bits-long-string
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    volumes:
      - ./backend:/app
    restart: on-failure

volumes:
  postgres_data:
  redis_data:
```

### 완료 확인
- [ ] 파일 생성 완료

---

## BT-04-02 | 백엔드 Dockerfile 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `backend/Dockerfile`

```dockerfile
# ── Build Stage ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Gradle wrapper + 의존성 캐시 레이어
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 및 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ── Run Stage ─────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 보안: non-root 유저
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

### 완료 확인
- [ ] `docker build -t chagok-backend ./backend` 성공

---

## BT-04-03 | Flyway 설정 및 마이그레이션 구조 준비

**작업 유형**: 파일/디렉토리 생성 (Claude Code 실행 가능)

### 디렉토리 구조 생성

```
backend/src/main/resources/
├── db/
│   ├── migration/            ← Flyway 마이그레이션 SQL (버전별)
│   │   └── V0.0.1__init_schema_placeholder.sql
│   └── seed/                 ← 시드 데이터 SQL (BT-07에서 채움)
│       └── R__portfolio_seed.sql
└── init/                     ← Docker 최초 실행 시 DB 초기화
    └── 01_create_db.sql
```

### 생성 파일: `db/migration/V0.0.1__init_schema_placeholder.sql`

```sql
-- Phase 0: 스키마 플레이스홀더
-- 실제 테이블은 BT-07 (DB 마이그레이션)에서 V1.0.0__ 이후로 추가

-- Flyway 동작 확인용 더미 테이블
CREATE TABLE IF NOT EXISTS flyway_check (
    id BIGSERIAL PRIMARY KEY,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 생성 파일: `init/01_create_db.sql`

```sql
-- Docker 최초 실행 시 DB 초기화 (이미 POSTGRES_DB로 생성되므로 확인만)
SELECT 'chagok_db database ready' AS status;
```

### application-local.yml Flyway 설정 확인

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

### 완료 확인
- [ ] 앱 실행 시 `Flyway: Successfully applied 1 migration` 로그 출력

---

## BT-04-04 | 로컬 실행 확인 및 .env 파일 생성

**작업 유형**: 명령어 실행 (Claude Code 실행 가능)

### .env 파일 생성 (로컬 개발용 — gitignore됨)

```bash
# 루트에 .env 파일 생성 (.env.example 복사 후 값 채우기)
cp .env.example .env
```

`.env` 파일에 로컬 값 채우기:
```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=chagok_db
DB_USERNAME=chagok
DB_PASSWORD=chagok1234
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=local-dev-secret-key-must-be-at-least-256-bits-long-string
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=2592000000
```

### Docker Compose 실행 및 확인

```bash
# 전체 스택 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f backend

# Health Check
curl http://localhost:8080/health

# Swagger UI 접근
open http://localhost:8080/swagger-ui.html

# PostgreSQL 접속 확인
docker exec -it chagok-postgres psql -U chagok -d chagok_db -c "\dt"

# Redis 확인
docker exec -it chagok-redis redis-cli ping
```

### 종료 명령어

```bash
# 컨테이너 종료 (데이터 유지)
docker-compose stop

# 컨테이너 + 볼륨 삭제 (초기화)
docker-compose down -v
```

### 완료 확인
- [ ] `docker-compose up -d` 오류 없음
- [ ] `curl http://localhost:8080/health` → `{"status":"UP"}`
- [ ] Swagger UI 페이지 로드
- [ ] Flyway 마이그레이션 성공 로그

---

## 완료 체크리스트

- [ ] BT-04-01: `docker-compose.yml` 작성 완료
- [ ] BT-04-02: `backend/Dockerfile` 작성 완료
- [ ] BT-04-03: Flyway 디렉토리 구조 + 플레이스홀더 마이그레이션 생성
- [ ] BT-04-04: `docker-compose up` → 전체 스택 정상 동작

**다음 태스크**: BT-07 (DB 마이그레이션)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
