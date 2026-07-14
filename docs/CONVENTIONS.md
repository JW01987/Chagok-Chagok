# 코딩 컨벤션

> 최초 작성일: 2026-07-14
> auto-pilot이 태스크를 자동 구현할 때 반드시 따라야 하는 규칙. AI 리뷰(readability/logic/exception_handling/test_code/security) 기준과 맞춰져 있음.

---

## 브랜치 / 커밋 / PR

- 브랜치명: `feature/bt-{번호}-auto`
- 커밋 메시지: `{type}: {설명}` — type은 `feat`, `fix`, `docs`, `chore`, `test`, `refactor` 중 하나
- PR은 하나의 Task(BT-xx) 단위로 생성. 여러 Task를 한 PR에 섞지 않음
- 수동으로만 할 수 있는 하위 작업(GitHub 웹 콘솔 설정, 실제 시크릿 값 등록 등)이 있으면 PR 본문에 남은 작업 목록으로 명시하고, 코드/설정으로 대체하려 하지 않음

## 작업 유형별 산출물

- **백엔드/프론트엔드 기능 구현**: 아래 "백엔드"/"프론트엔드" 규칙 + 테스트 코드 필수
- **설정/인프라 파일 생성** (`.gitignore`, `dependabot.yml`, CI 워크플로우 등): 해당 파일만 생성. 불필요한 코드나 테스트를 만들지 않음
- **문서 작업**: 해당 마크다운 파일만 수정

---

## 백엔드 (Java 17 + Spring Boot 3.x)

### 레이어 구조

```
controller/  → HTTP 요청/응답만 담당, 비즈니스 로직 없음
service/     → 비즈니스 로직, @Transactional은 여기에서만
repository/  → JPA Repository 인터페이스
dto/         → request/response DTO — 엔티티를 직접 노출하지 않음
entity/      → JPA 엔티티
exception/   → 커스텀 예외 + @RestControllerAdvice 전역 핸들러
```

### 네이밍

- 클래스: PascalCase (`UserService`, `InvestmentController`)
- 메서드/변수: camelCase
- 상수: UPPER_SNAKE_CASE
- Controller: `{Domain}Controller`, Service: `{Domain}Service` (+구현체가 필요하면 `{Domain}ServiceImpl`)
- DTO: 요청은 `{Domain}Request`, 응답은 `{Domain}Response`

### 예외 처리

- 비즈니스 예외는 `RuntimeException`을 상속한 커스텀 예외로 던짐 (예: `UserNotFoundException`)
- 커스텀 예외는 에러 코드(enum) + 메시지를 가짐
- 전역 핸들러(`@RestControllerAdvice`)에서 일괄 처리 — Controller에서 try/catch로 삼키지 않음
- 클라이언트에는 스택트레이스 노출 금지, 정의된 에러 응답 포맷만 반환

### API 응답 포맷

- 성공: `{ "success": true, "data": ... }`
- 실패: `{ "success": false, "error": { "code": "...", "message": "..." } }`

### 테스트

- JUnit5 + Mockito
- Service 레이어는 단위 테스트 필수, 커버리지 80% 이상
- Repository는 `@DataJpaTest`로 검증 (필요한 경우)
- 테스트 메서드명: `should_기대결과_when_조건` 또는 한글 `@DisplayName` 사용

### 보안

- 시크릿(DB 비밀번호, JWT 키, API 키 등)은 절대 하드코딩하지 않고 환경변수로 주입
- 사용자 입력은 검증(`@Valid` + Bean Validation) 후 사용
- SQL은 JPA/QueryDSL 사용 — native query에 문자열 concat 금지 (SQL Injection 방지)
- 인증이 필요한 엔드포인트는 Spring Security 설정에서 명시적으로 보호

---

## 프론트엔드 (React Native / Expo)

- 상태 관리: 전역 상태는 Zustand, 서버 상태는 TanStack Query — 직접 혼용해서 서버 데이터를 Zustand에 복제하지 않음
- 컴포넌트: PascalCase 파일명, 함수형 컴포넌트 + Hooks만 사용 (클래스 컴포넌트 금지)
- 디렉토리: `screens/`, `components/`, `hooks/`, `stores/`, `api/`
- API 호출은 `api/` 레이어로 분리 — 컴포넌트에서 직접 fetch 금지
- 스타일은 `StyleSheet.create` 사용

---

## 공통

- 커밋되는 코드에 콘솔 로그/디버그 코드 남기지 않기
- `.env` 값은 예시(`.env.example`)만 커밋, 실제 값은 절대 커밋 금지
- 새 의존성 추가 시 이유를 커밋 메시지나 PR 본문에 간단히 남기기
