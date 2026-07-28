# BT-04 | Docker Compose 로컬 환경

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-07-28
- **Phase**: 0 — 환경 세팅
- **상태**: ✅ 완료 (`feature/bt-04-auto`)
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

- [x] BT-04-01: `docker-compose.yml` 작성 완료
- [x] BT-04-02: `backend/Dockerfile` 작성 완료
- [x] BT-04-03: Flyway 디렉토리 구조 + 플레이스홀더 마이그레이션 생성
- [x] BT-04-04: `docker-compose up -d --build` → 전체 스택 정상 동작 (postgres/redis healthy, backend UP) — 로컬에서 직접 실행하여 검증 완료

### 검증 결과 (로컬 Docker Desktop, 2026-07-28)

```
$ docker compose up -d --build
...
 Container chagok-backend  Started

$ curl http://localhost:8080/health
{"status":"UP","timestamp":"...","service":"chagok-backend"}   # HTTP 200

$ curl -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui/index.html
200

$ docker exec chagok-postgres psql -U chagok -d chagok_db -c "\dt"
 public | flyway_check          | table
 public | flyway_schema_history | table
```

### 원본 스펙 대비 변경 사항 (동작 검증 중 발견한 문제 수정)

- **`backend/build.gradle`**: `org.springframework.boot:spring-boot-flyway` 의존성 추가. Spring Boot 4부터 Flyway 자동 설정이 `spring-boot-autoconfigure`에서 별도 모듈로 분리되어, `flyway-core`/`flyway-database-postgresql`만으로는 마이그레이션이 전혀 실행되지 않음(로그에 Flyway 관련 출력이 전혀 없었음 — 실측으로 발견).
- **`backend/src/main/resources/application-local.yml`**: datasource URL/redis host를 `${DB_HOST:localhost}` 형태의 환경변수 오버라이드로 변경. 기존에는 `localhost`가 하드코딩되어 있어 `local` 프로파일로 컨테이너 안에서 실행 시 `postgres`/`redis` 컨테이너에 연결할 수 없었음(docker-compose가 `SPRING_PROFILES_ACTIVE=local` + `DB_HOST=postgres` 등을 주입하는 스펙과 충돌).
- **`docker-compose.yml`**: `backend` 서비스에 `platform: linux/amd64` 고정. `eclipse-temurin:17-jdk-alpine`/`17-jre-alpine`이 amd64 manifest만 제공해 Apple Silicon(arm64) 호스트에서 빌드가 아예 실패했음 — CI/Linux amd64에서는 네이티브로, arm64 로컬 환경에서는 에뮬레이션으로 동작.
- **`docker-compose.yml`**: `backend.volumes: - ./backend:/app` 라인 제거. 멀티스테이지 빌드로 만든 런타임 이미지의 `/app/app.jar`를 호스트 소스 디렉터리로 통째로 덮어써 컨테이너가 기동할 수 없게 만드는 설정이라 제외함.
- `version: '3.9'` 최상단 키 제거 (최신 Docker Compose에서 obsolete 경고 발생, 기능에는 영향 없음).

**다음 태스크**: BT-07 (DB 마이그레이션)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-07-28_
