# BT-13 | 유저 포트폴리오 선택

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-08 (Auth), BT-11 (포트폴리오)
- **완료 기준**: 포트폴리오 선택/변경/조회 API 동작, 동시 ACTIVE 1개 제한

---

## BT-13-01 | 포트폴리오 선택 API

**엔드포인트**: `POST /api/user-portfolios`
**인증**: 필요

### Request DTO

```java
@Getter
public class UserPortfolioSelectRequest {
    @NotNull
    private Long portfolioTemplateId;
}
```

### Service

```java
@Transactional
public UserPortfolioResponse selectPortfolio(Long userId, Long portfolioTemplateId) {
    // 포트폴리오 존재 여부 확인
    PortfolioTemplate template = portfolioTemplateRepository.findById(portfolioTemplateId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

    User user = userRepository.findById(userId).orElseThrow();

    // 기존 ACTIVE 포트폴리오 TERMINATED 처리
    userPortfolioRepository.findByUserIdAndStatus(userId, PortfolioStatus.ACTIVE)
        .ifPresent(existing -> {
            existing.terminate();
            userPortfolioRepository.save(existing);
        });

    // 새 포트폴리오 선택
    UserPortfolio userPortfolio = UserPortfolio.builder()
        .user(user)
        .portfolioTemplate(template)
        .status(PortfolioStatus.ACTIVE)
        .selectedAt(LocalDateTime.now())
        .build();

    return UserPortfolioResponse.of(userPortfolioRepository.save(userPortfolio), template);
}
```

### Controller

```java
@RestController
@RequestMapping("/api/user-portfolios")
@RequiredArgsConstructor
@Tag(name = "UserPortfolio", description = "유저 포트폴리오 API")
public class UserPortfolioController {

    private final UserPortfolioService userPortfolioService;

    @PostMapping
    @Operation(summary = "포트폴리오 선택")
    public ResponseEntity<ApiResponse<UserPortfolioResponse>> selectPortfolio(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserPortfolioSelectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(userPortfolioService.selectPortfolio(userId, request.getPortfolioTemplateId())));
    }

    @GetMapping("/active")
    @Operation(summary = "현재 활성 포트폴리오 조회")
    public ResponseEntity<ApiResponse<UserPortfolioResponse>> getActivePortfolio(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userPortfolioService.getActivePortfolio(userId)));
    }

    @PatchMapping("/{id}/terminate")
    @Operation(summary = "포트폴리오 중단")
    public ResponseEntity<ApiResponse<Void>> terminatePortfolio(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        userPortfolioService.terminate(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "포트폴리오가 중단되었습니다"));
    }
}
```

---

## BT-13-02 | 포트폴리오 변경 API

기존 ACTIVE를 TERMINATED로, 새 포트폴리오를 ACTIVE로 — 동일한 `selectPortfolio` 메서드로 처리.

### UserPortfolio 엔티티 메서드

```java
@Entity
@Table(name = "user_portfolios")
public class UserPortfolio extends BaseTimeEntity {

    // ... 필드 생략

    public void terminate() {
        this.status = PortfolioStatus.TERMINATED;
        this.terminatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == PortfolioStatus.ACTIVE;
    }
}
```

---

## BT-13-03 | 현재 ACTIVE 포트폴리오 조회

```java
public UserPortfolioResponse getActivePortfolio(Long userId) {
    UserPortfolio userPortfolio = userPortfolioRepository
        .findByUserIdAndStatus(userId, PortfolioStatus.ACTIVE)
        .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_PORTFOLIO_NOT_FOUND));

    return UserPortfolioResponse.of(userPortfolio, userPortfolio.getPortfolioTemplate());
}
```

### Response DTO

```java
@Getter @Builder
public class UserPortfolioResponse {
    private Long userPortfolioId;
    private Long portfolioTemplateId;
    private String portfolioCode;
    private String portfolioName;
    private String status;
    private LocalDateTime selectedAt;

    public static UserPortfolioResponse of(UserPortfolio up, PortfolioTemplate t) {
        return UserPortfolioResponse.builder()
            .userPortfolioId(up.getId())
            .portfolioTemplateId(t.getId())
            .portfolioCode(t.getCode())
            .portfolioName(t.getName())
            .status(up.getStatus().name())
            .selectedAt(up.getSelectedAt())
            .build();
    }
}
```

---

## 완료 체크리스트

- [ ] BT-13-01: `POST /api/user-portfolios` — 선택 시 기존 ACTIVE → TERMINATED
- [ ] BT-13-02: `GET /api/user-portfolios/active` — 현재 ACTIVE 조회
- [ ] BT-13-03: `PATCH /api/user-portfolios/{id}/terminate` — 중단
- [ ] 동시 ACTIVE 1개 제한 검증

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
