# BT-01 | GitHub 저장소 구성

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 0 — 환경 세팅
- **상태**: ⬜ 대기
- **완료 기준**: 저장소 생성 + 브랜치 보호 + .gitignore + .env.example + Dependabot 활성화

---

## 개요

GitHub 저장소를 monorepo 구조로 생성하고, 시크릿 유출 방지 및 브랜치 보호 규칙을 설정한다.
이후 모든 작업의 기반이 되는 태스크.

---

## BT-01-01 | 저장소 생성 및 monorepo 구조 결정

**작업 유형**: 수동 (GitHub 웹)

### 실행 순서

1. GitHub → New repository
   - Name: `chagok-chagok`
   - Visibility: Public (또는 Private — 선택)
   - Initialize with README: ✅
   - .gitignore: None (직접 작성)
   - License: MIT

2. monorepo 디렉토리 구조 생성

```
chagok-chagok/
├── backend/          ← Spring Boot 프로젝트
├── frontend/         ← React Native (Expo) 프로젝트
├── docs/             ← 문서 (기획서, ERD 등)
├── .github/
│   ├── workflows/    ← GitHub Actions
│   └── dependabot.yml
├── .gitignore
├── .env.example
└── README.md
```

3. 로컬 clone

```bash
git clone https://github.com/{username}/chagok-chagok.git
cd chagok-chagok
mkdir -p backend frontend docs .github/workflows
```

### 완료 확인
- [ ] `git remote -v` 에서 origin 확인
- [ ] 디렉토리 구조 생성 완료

---

## BT-01-02 | .gitignore 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `/.gitignore`

```gitignore
# ========================
# 환경 변수 (절대 커밋 금지)
# ========================
.env
.env.*
!.env.example

# ========================
# Backend (Spring Boot)
# ========================
backend/build/
backend/.gradle/
backend/out/
backend/*.jar
backend/*.war
backend/HELP.md
backend/.idea/
backend/*.iml
backend/.DS_Store

# ========================
# Frontend (React Native / Expo)
# ========================
frontend/node_modules/
frontend/.expo/
frontend/dist/
frontend/build/
frontend/.DS_Store
frontend/*.jks
frontend/*.p8
frontend/*.p12
frontend/*.key
frontend/*.mobileprovision
frontend/*.orig.*
frontend/web-build/
frontend/.metro-health-check*

# ========================
# OS
# ========================
.DS_Store
Thumbs.db

# ========================
# IDE
# ========================
.idea/
*.iml
.vscode/
*.swp
*.swo
```

### 완료 확인
- [ ] `git status` 에서 .env 파일이 추적되지 않음

---

## BT-01-03 | .env.example 작성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `/.env.example`

```dotenv
# ========================
# Backend — Database
# ========================
DB_HOST=localhost
DB_PORT=5432
DB_NAME=chagok_db
DB_USERNAME=
DB_PASSWORD=

# ========================
# Backend — Redis
# ========================
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ========================
# Backend — JWT
# ========================
JWT_SECRET=
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=2592000000

# ========================
# Backend — OAuth2 (카카오)
# ========================
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=

# ========================
# Backend — OAuth2 (Apple)
# ========================
APPLE_CLIENT_ID=
APPLE_TEAM_ID=
APPLE_KEY_ID=
APPLE_PRIVATE_KEY=

# ========================
# Backend — AWS
# ========================
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=
# IAM Role 사용 — Access Key 하드코딩 금지

# ========================
# Backend — FCM
# ========================
FCM_CREDENTIALS_PATH=

# ========================
# Frontend
# ========================
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
EXPO_PUBLIC_ENV=local
```

### 완료 확인
- [ ] `.env.example` 커밋됨
- [ ] 실제 값은 없고 키만 존재

---

## BT-01-04 | 브랜치 보호 규칙 설정

**작업 유형**: 수동 (GitHub 웹)

### main 브랜치 보호

GitHub → Settings → Branches → Add rule

- Branch name pattern: `main`
- ✅ Require a pull request before merging
  - ✅ Require approvals: 1
- ✅ Require status checks to pass before merging
  - 추가할 체크: `backend-ci`, `frontend-ci`, `gitleaks` (BT-05 완료 후 등록)
- ✅ Require branches to be up to date before merging
- ✅ Do not allow bypassing the above settings

### develop 브랜치 보호

동일하게 적용 (approvals는 선택)

### develop 브랜치 생성

```bash
git checkout -b develop
git push -u origin develop
```

### 완료 확인
- [ ] `main` 직접 push 시도 → 거부됨
- [ ] `develop` 브랜치 원격 생성 확인

---

## BT-01-05 | GitHub Secrets 등록

**작업 유형**: 수동 (GitHub 웹)

GitHub → Settings → Secrets and variables → Actions → New repository secret

### 등록할 Secrets

| Secret 이름 | 설명 | 예시 |
|---|---|---|
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `ECR_REGISTRY` | ECR 레지스트리 URL | `123456789.dkr.ecr.ap-northeast-2.amazonaws.com` |
| `ECR_REPOSITORY` | ECR 저장소 이름 | `chagok-backend` |
| `EC2_HOST` | 개발 서버 IP | `x.x.x.x` |
| `EC2_USER` | EC2 접속 유저 | `ubuntu` |
| `EC2_SSH_KEY` | EC2 PEM 키 내용 | (BT-06에서 생성 후 등록) |
| `DB_PASSWORD` | 개발 DB 비밀번호 | |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) | |

> BT-06 (CD 파이프라인) 완료 후 EC2 관련 Secrets 추가 등록

### 완료 확인
- [ ] GitHub → Settings → Secrets 목록에서 확인

---

## BT-01-06 | Dependabot 활성화

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `/.github/dependabot.yml`

```yaml
version: 2
updates:
  # Backend (Gradle)
  - package-ecosystem: "gradle"
    directory: "/backend"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 5
    labels:
      - "dependencies"
      - "backend"

  # Frontend (npm)
  - package-ecosystem: "npm"
    directory: "/frontend"
    schedule:
      interval: "weekly"
      day: "monday"
    open-pull-requests-limit: 5
    labels:
      - "dependencies"
      - "frontend"

  # GitHub Actions
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    labels:
      - "dependencies"
      - "ci"
```

### 완료 확인
- [ ] GitHub → Insights → Dependency graph → Dependabot 탭에서 활성화 확인

---

## 완료 체크리스트

- [ ] BT-01-01: 저장소 생성 + monorepo 디렉토리 구조
- [ ] BT-01-02: `.gitignore` 커밋
- [ ] BT-01-03: `.env.example` 커밋
- [ ] BT-01-04: 브랜치 보호 규칙 (`main`, `develop`)
- [ ] BT-01-05: GitHub Secrets 등록
- [ ] BT-01-06: Dependabot 활성화

**다음 태스크**: BT-02 (백엔드 초기화), BT-03 (프론트엔드 초기화) — 병렬 진행 가능

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
