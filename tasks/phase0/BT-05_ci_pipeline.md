# BT-05 | CI 파이프라인 구축

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 0 — 환경 세팅
- **상태**: ⬜ 대기
- **선행 태스크**: BT-02, BT-03
- **완료 기준**: PR 생성 시 CI 자동 실행 + GitLeaks 시크릿 탐지 시 빌드 실패

---

## 개요

GitHub Actions를 사용하여 PR 시 자동으로 실행되는 CI 파이프라인을 구축한다.
백엔드 빌드/테스트, 프론트엔드 타입체크, GitLeaks 시크릿 스캔을 포함한다.

---

## BT-05-01 | 백엔드 CI 워크플로 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `.github/workflows/ci.yml`

```yaml
name: CI

on:
  pull_request:
    branches: [ main, develop ]
  push:
    branches: [ develop ]

jobs:

  # ──────────────────────────────────────
  # 1. GitLeaks 시크릿 스캔 (가장 먼저 실행)
  # ──────────────────────────────────────
  gitleaks:
    name: GitLeaks Secret Scan
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0  # 전체 히스토리 스캔

      - name: Run GitLeaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  # ──────────────────────────────────────
  # 2. 백엔드 CI
  # ──────────────────────────────────────
  backend-ci:
    name: Backend CI
    runs-on: ubuntu-latest
    needs: gitleaks  # 시크릿 스캔 통과 후 실행

    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: chagok_test_db
          POSTGRES_USER: chagok
          POSTGRES_PASSWORD: chagok1234
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 3s
          --health-retries 3

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Grant execute permission for Gradle
        run: chmod +x backend/gradlew

      - name: Build with Gradle
        working-directory: backend
        run: ./gradlew build --no-daemon
        env:
          SPRING_PROFILES_ACTIVE: test
          DB_HOST: localhost
          DB_PORT: 5432
          DB_NAME: chagok_test_db
          DB_USERNAME: chagok
          DB_PASSWORD: chagok1234
          REDIS_HOST: localhost
          REDIS_PORT: 6379
          JWT_SECRET: test-secret-key-must-be-at-least-256-bits-long-string

      - name: Run Tests
        working-directory: backend
        run: ./gradlew test --no-daemon
        env:
          SPRING_PROFILES_ACTIVE: test
          DB_HOST: localhost
          DB_PORT: 5432
          DB_NAME: chagok_test_db
          DB_USERNAME: chagok
          DB_PASSWORD: chagok1234
          REDIS_HOST: localhost
          REDIS_PORT: 6379
          JWT_SECRET: test-secret-key-must-be-at-least-256-bits-long-string

      - name: Publish Test Report
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Backend Tests
          path: backend/build/test-results/test/*.xml
          reporter: java-junit

  # ──────────────────────────────────────
  # 3. 프론트엔드 CI
  # ──────────────────────────────────────
  frontend-ci:
    name: Frontend CI
    runs-on: ubuntu-latest
    needs: gitleaks

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: TypeScript type check
        working-directory: frontend
        run: npm run typecheck

      - name: Lint
        working-directory: frontend
        run: npm run lint || true  # MVP 단계에서는 경고 허용
```

### 완료 확인
- [ ] 파일 생성 후 GitHub에 push
- [ ] GitHub → Actions 탭에서 워크플로 확인

---

## BT-05-02 | 테스트용 application-test.yml 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `backend/src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:chagok_test_db}
    username: ${DB_USERNAME:chagok}
    password: ${DB_PASSWORD:chagok1234}
  jpa:
    hibernate:
      ddl-auto: create-drop  # 테스트마다 초기화
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
    clean-on-validation-error: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

logging:
  level:
    com.chagok: WARN
    org.springframework.security: WARN

jwt:
  secret: ${JWT_SECRET:test-secret-key-must-be-at-least-256-bits-long-string}
  access-expiration-ms: 900000
  refresh-expiration-ms: 2592000000
```

### 완료 확인
- [ ] `./gradlew test` 로컬 실행 성공

---

## BT-05-03 | GitLeaks 설정 파일 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `.gitleaks.toml` (루트)

```toml
title = "차곡차곡 GitLeaks 설정"

[extend]
# 기본 규칙 사용
useDefault = true

[[allowlists]]
description = "테스트/예시 파일 허용"
paths = [
  ".env.example",
  "src/test/.*",
  "docs/.*"
]

[[allowlists]]
description = "로컬 개발용 더미 시크릿 허용"
regexes = [
  "local-dev-secret-key",
  "test-secret-key",
  "chagok1234"
]
```

### 완료 확인
- [ ] `.gitleaks.toml` 커밋
- [ ] 테스트: 가짜 API 키를 커밋 시도 → CI에서 GitLeaks 탐지 확인

---

## BT-05-04 | CI 동작 검증

**작업 유형**: 수동 검증

### 검증 절차

```bash
# 1. feature 브랜치 생성
git checkout -b feature/ci-test

# 2. 더미 파일 수정
echo "# CI Test" >> README.md
git add README.md
git commit -m "test: CI 파이프라인 동작 확인"
git push origin feature/ci-test

# 3. GitHub에서 develop으로 PR 생성
```

### 확인 항목

- [ ] PR 생성 시 3개 Job 자동 실행 (gitleaks / backend-ci / frontend-ci)
- [ ] 모든 Job 통과 (초록색 체크)
- [ ] 머지 버튼 활성화 (브랜치 보호 규칙 연동)

### 시크릿 스캔 동작 확인 (선택)

```bash
# 테스트용 가짜 AWS 키 삽입
echo "AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY" >> test_secret.txt
git add test_secret.txt
git commit -m "test: gitleaks 탐지 확인용"
git push

# CI에서 gitleaks Job 실패 확인 후 커밋 revert
git revert HEAD
git push
```

---

## 완료 체크리스트

- [ ] BT-05-01: `ci.yml` 생성 + GitHub Actions에서 워크플로 인식
- [ ] BT-05-02: `application-test.yml` 생성
- [ ] BT-05-03: `.gitleaks.toml` 생성
- [ ] BT-05-04: 테스트 PR에서 CI 전체 통과 확인

**다음 태스크**: BT-06 (CD 파이프라인)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
