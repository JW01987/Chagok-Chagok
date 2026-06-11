# auto-pilot 사용 가이드

> 최초 작성일: 2026-06-08
> 업데이트: 2026-06-08

---

## 이게 뭔가요

`scripts/auto-pilot.js` 는 진이 컴퓨터에서 **계속 켜두는** 자동화 데몬입니다.

흐름:

```
PR 생성
   ↓
GitHub Actions: Gemini 리뷰 → 점수/요약을 텔레그램으로 전송
   ↓
텔레그램에 [✅ 통과] [🔄 다시 생성] [⏸️ 보류] 버튼 도착
   ↓
진이가 버튼 클릭
   ↓
auto-pilot.js 가 클릭을 감지해서:
   ✅ 통과    → PR 머지 + TASKS.md에서 다음 "대기" Task를 찾아 Claude Code 자동 실행
   🔄 다시 생성 → 리뷰 피드백을 담아 같은 브랜치에서 Claude Code 재실행
   ⏸️ 보류    → 아무 것도 안 하고 대기 (나중에 직접 처리)
```

**자동 머지는 GitHub Actions가 아니라 진이의 최종 승인(버튼 클릭) 이후에만 일어납니다.**
점수 기준 통과 여부는 참고용으로만 표시되고, 최종 결정은 항상 사람이 합니다.

---

## 사전 준비 (한 번만)

### 1. 로컬에 필요한 도구 설치 + 인증

```bash
# git, gh(GitHub CLI), claude(Claude Code CLI) 가 모두 설치 + 로그인되어 있어야 함
gh auth status
claude --version
```

### 2. 환경변수 파일 만들기

`.env.auto-pilot.example` 을 복사해서 `.env.auto-pilot` 으로 저장하고 값 채우기:

```bash
cp .env.auto-pilot.example .env.auto-pilot
```

| 키                   | 값 출처                                                                   |
| -------------------- | ------------------------------------------------------------------------- |
| `TELEGRAM_BOT_TOKEN` | BotFather에서 발급받은 토큰 (CONVENTIONS 파이프라인과 동일)               |
| `TELEGRAM_CHAT_ID`   | `getUpdates` API로 확인한 chat id                                         |
| `GITHUB_TOKEN`       | GitHub Personal Access Token (repo 권한) — `gh auth token` 으로 확인 가능 |
| `REPO`               | `사용자명/레포명` 형식                                                    |

⚠️ **`.env.auto-pilot` 은 절대 커밋하지 마세요.** `.gitignore`에 추가되어 있는지 꼭 확인!

### 3. 패키지 설치

```bash
npm install dotenv node-fetch
```

---

## 실행하기

레포 루트 디렉토리에서:

```bash
node scripts/auto-pilot.js
```

터미널을 계속 켜두거나, 백그라운드로 돌리고 싶으면:

```bash
# nohup으로 백그라운드 실행 (로그는 auto-pilot.log에 쌓임)
nohup node scripts/auto-pilot.js > auto-pilot.log 2>&1 &

# 종료하고 싶을 때
pkill -f "scripts/auto-pilot.js"
```

또는 `pm2` 같은 프로세스 매니저를 쓰면 컴퓨터 재부팅 후에도 자동 재시작되도록 설정할 수 있어요.

---

## 동작 확인하기

1. Claude Code로 Task 하나를 구현 → PR 생성
2. GitHub Actions가 자동으로 Gemini 리뷰 실행
3. 텔레그램으로 점수 + 버튼 메시지 도착
4. 버튼 클릭 → 터미널(auto-pilot 실행 중인 곳)에 로그가 찍히는지 확인
5. 통과를 눌렀다면:
   - PR이 머지되는지 (`gh pr view {번호}`)
   - 다음 Task 번호를 잡아서 Claude Code가 자동 실행되는지

---

## 주의할 점

- **claude CLI 헤드리스 옵션**: `auto-pilot.js`는 `claude -p "프롬프트"` 형태로 실행합니다. 사용 중인 Claude Code 버전에 따라 플래그가 다를 수 있으니, `claude --help`로 비대화형(non-interactive/print) 실행 옵션을 확인하고 다르면 `runClaudeCode` 함수의 인자를 수정하세요.
- **브랜치 충돌**: "다시 생성"을 누르면 로컬에서 해당 PR 브랜치로 `checkout` + `pull`을 시도합니다. 로컬에 커밋되지 않은 변경사항이 있으면 실패할 수 있으니, auto-pilot을 켜둔 디렉토리는 다른 작업에 쓰지 않는 걸 추천해요.
- **중복 실행 방지**: 같은 콜백을 여러 번 처리하지 않도록 `.auto-pilot-state.json`에 처리 이력을 저장합니다. 이 파일도 커밋하지 마세요.
- **다음 Task가 없을 때**: TASKS.md에 "상태: 대기"인 항목이 없으면 자동 실행 없이 알림만 옵니다. 새 Task를 추가해주세요.
- **브랜치명**: 자동 생성되는 브랜치명은 `feature/task-{번호}-auto` 형식입니다. 더 의미 있는 이름을 쓰고 싶다면 `auto-pilot.js`의 `slugify` 함수를 직접 영문 설명을 넣도록 수정하세요.

---

## 만약 사람이 개입해야 하면

- auto-pilot은 오류가 나면 멈추지 않고 텔레그램으로 🚨 알림을 보냅니다.
- 알림이 오면 터미널 로그(`auto-pilot.log`)를 확인하고 직접 처리한 뒤, 다시 진행 상황에 맞게 버튼을 누르거나 수동으로 작업을 이어가면 됩니다.

---

## 예시 코드

- docs/auto-pilot/ 폴더 안을 확인
