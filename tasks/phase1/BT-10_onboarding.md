# BT-10 | 온보딩

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-08 (Auth)
- **완료 기준**: 온보딩 저장/조회/수정 API 동작 + 게스트→회원 마이그레이션 처리

---

## 개요

유저의 투자 성향·목표·금액을 저장하는 온보딩 API를 구현한다.
게스트 온보딩 데이터(앱 로컬)는 회원가입 완료 시 서버로 마이그레이션된다.

---

## BT-10-01 | 온보딩 저장 API

**엔드포인트**: `POST /api/users/onboarding`
**인증**: 필요 (Bearer Token)

### Request DTO

```java
@Getter
public class OnboardingRequest {

    @NotNull
    private InvestmentType investmentType;   // SAFE | BALANCED | GROWTH | KOREA_FOCUSED

    @NotNull @Min(50000) @Max(1000000)
    private Integer monthlyAmount;

    @NotNull
    private GoalType goalType;               // LUMP_SUM | RETIREMENT | FREE

    @Min(0)
    private Integer goalAmount;              // nullable

    @Min(1) @Max(360)
    private Integer goalPeriodMonths;        // nullable

    @Min(1) @Max(28)
    private Integer salaryDay;              // nullable
}
```

### Service

```java
@Transactional
public OnboardingResponse saveOnboarding(Long userId, OnboardingRequest request) {
    // 중복 온보딩 체크
    if (userOnboardingRepository.existsByUserId(userId)) {
        throw new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
    }

    User user = userRepository.findById(userId).orElseThrow();

    UserOnboarding onboarding = UserOnboarding.builder()
        .user(user)
        .investmentType(request.getInvestmentType())
        .monthlyAmount(request.getMonthlyAmount())
        .goalType(request.getGoalType())
        .goalAmount(request.getGoalAmount())
        .goalPeriodMonths(request.getGoalPeriodMonths())
        .salaryDay(request.getSalaryDay())
        .onboardingCompletedAt(LocalDateTime.now())
        .build();

    userOnboardingRepository.save(onboarding);

    // 성향에 맞는 추천 포트폴리오 ID 반환
    PortfolioTemplate recommended = portfolioTemplateRepository
        .findByInvestmentTypeAndIsActive(request.getInvestmentType(), true)
        .stream().findFirst().orElseThrow();

    return new OnboardingResponse(onboarding, recommended.getId());
}
```

### Controller

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "유저 API")
public class UserController {

    private final OnboardingService onboardingService;

    @PostMapping("/onboarding")
    @Operation(summary = "온보딩 저장")
    public ResponseEntity<ApiResponse<OnboardingResponse>> saveOnboarding(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody OnboardingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(onboardingService.saveOnboarding(userId, request)));
    }

    @GetMapping("/onboarding")
    @Operation(summary = "온보딩 조회")
    public ResponseEntity<ApiResponse<OnboardingResponse>> getOnboarding(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.getOnboarding(userId)));
    }

    @PatchMapping("/onboarding")
    @Operation(summary = "온보딩 수정")
    public ResponseEntity<ApiResponse<OnboardingResponse>> updateOnboarding(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody OnboardingUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(onboardingService.updateOnboarding(userId, request)));
    }
}
```

> `@AuthenticationPrincipal Long userId` 동작을 위해 `HandlerMethodArgumentResolver` 또는 Custom Annotation 구현 필요

---

## BT-10-02 | 온보딩 조회 API

**엔드포인트**: `GET /api/users/onboarding`

```java
public OnboardingResponse getOnboarding(Long userId) {
    UserOnboarding onboarding = userOnboardingRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ONBOARDING_NOT_FOUND));
    return OnboardingResponse.from(onboarding);
}
```

### Response DTO

```java
@Getter
@Builder
public class OnboardingResponse {
    private Long onboardingId;
    private InvestmentType investmentType;
    private Integer monthlyAmount;
    private GoalType goalType;
    private Integer goalAmount;
    private Integer goalPeriodMonths;
    private Integer salaryDay;
    private LocalDateTime completedAt;
    private Long recommendedPortfolioId;  // 추천 포트폴리오
}
```

---

## BT-10-03 | 온보딩 수정 API

**엔드포인트**: `PATCH /api/users/onboarding`

```java
@Transactional
public OnboardingResponse updateOnboarding(Long userId, OnboardingUpdateRequest request) {
    UserOnboarding onboarding = userOnboardingRepository.findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ONBOARDING_NOT_FOUND));

    // null이 아닌 필드만 업데이트 (PATCH 방식)
    if (request.getInvestmentType() != null) onboarding.setInvestmentType(request.getInvestmentType());
    if (request.getMonthlyAmount()  != null) onboarding.setMonthlyAmount(request.getMonthlyAmount());
    if (request.getGoalType()       != null) onboarding.setGoalType(request.getGoalType());
    if (request.getGoalAmount()     != null) onboarding.setGoalAmount(request.getGoalAmount());
    if (request.getGoalPeriodMonths() != null) onboarding.setGoalPeriodMonths(request.getGoalPeriodMonths());
    if (request.getSalaryDay()      != null) onboarding.setSalaryDay(request.getSalaryDay());

    return OnboardingResponse.from(userOnboardingRepository.save(onboarding));
}
```

---

## BT-10-04 | 게스트 온보딩 마이그레이션

게스트가 앱 로컬(AsyncStorage)에 저장해둔 온보딩 데이터를 회원가입 시 서버로 전송한다.

### 회원가입 Request에 온보딩 데이터 추가

```java
@Getter
public class SignupRequest {
    // 기존 필드
    @Email @NotBlank private String email;
    @Size(min = 8) @NotBlank private String password;
    @NotBlank private String nickname;

    // 게스트 온보딩 데이터 (nullable — 있으면 자동 마이그레이션)
    private OnboardingRequest onboarding;
}
```

### AuthService.signup() 수정

```java
// 온보딩 데이터가 있으면 자동 저장
if (request.getOnboarding() != null) {
    onboardingService.saveOnboarding(user.getId(), request.getOnboarding());
}
```

---

## BT-10-05 | 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Test
    @DisplayName("월 투자 금액 50,000원 미만 시 400 오류")
    void saveOnboarding_amountTooLow_throws400() {
        // Bean Validation으로 처리되므로 Controller 레이어 테스트
    }

    @Test
    @DisplayName("중복 온보딩 시 ONBOARDING_ALREADY_COMPLETED 예외")
    void saveOnboarding_duplicate_throwsException() {
        given(userOnboardingRepository.existsByUserId(1L)).willReturn(true);
        assertThatThrownBy(() -> onboardingService.saveOnboarding(1L, validRequest()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("온보딩 완료 시 추천 포트폴리오 ID 반환")
    void saveOnboarding_returnsRecommendedPortfolio() {
        // given
        given(userOnboardingRepository.existsByUserId(1L)).willReturn(false);
        given(portfolioTemplateRepository.findByInvestmentTypeAndIsActive(BALANCED, true))
            .willReturn(List.of(mockPortfolio(2L)));
        // when
        OnboardingResponse response = onboardingService.saveOnboarding(1L, balancedRequest());
        // then
        assertThat(response.getRecommendedPortfolioId()).isEqualTo(2L);
    }
}
```

---

## 완료 체크리스트

- [ ] BT-10-01: `POST /api/users/onboarding` — 409 중복 방지, 추천 포트폴리오 반환
- [ ] BT-10-02: `GET /api/users/onboarding` — 조회
- [ ] BT-10-03: `PATCH /api/users/onboarding` — 수정
- [ ] BT-10-04: 회원가입 시 게스트 온보딩 자동 마이그레이션
- [ ] BT-10-05: 단위 테스트 통과

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
