# BT-14 | 적립 설정

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-13 (유저 포트폴리오 선택)
- **완료 기준**: 적립 저장/조회/수정/일시중지/건너뛰기 API + next_schedule_at 계산

---

## BT-14-01 | 적립 설정 저장 API

**엔드포인트**: `POST /api/savings/plans`

### Request DTO

```java
@Getter
public class SavingsPlanRequest {

    @NotNull
    private Long userPortfolioId;

    @NotNull @Min(10000)
    private Integer amount;

    @NotNull
    private AmountType amountType;   // FIXED | SALARY_RATIO

    @DecimalMin("1.0") @DecimalMax("100.0")
    private Double salaryRatio;      // amountType=SALARY_RATIO 시 필수

    @NotNull
    private Frequency frequency;     // MONTHLY | BIMONTHLY | WEEKLY

    @Min(1) @Max(28)
    private Integer dayOfMonth;      // MONTHLY/BIMONTHLY 시 필수

    @Min(0) @Max(6)
    private Integer dayOfWeek;       // WEEKLY 시 필수 (0=월~6=일)

    @NotNull
    private PurchaseMethod purchaseMethod; // LUMP_SUM | DCA

    @Min(2) @Max(12)
    private Integer dcaCount;        // DCA 시 필수, 기본 4
}
```

### Service

```java
@Transactional
public SavingsPlanResponse createPlan(Long userId, SavingsPlanRequest request) {
    // 유저 포트폴리오 소유권 확인
    UserPortfolio userPortfolio = userPortfolioRepository.findById(request.getUserPortfolioId())
        .filter(up -> up.getUser().getId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

    // 기존 ACTIVE 플랜이 있으면 TERMINATED
    savingsPlanRepository.findByUserIdAndStatus(userId, SavingsStatus.ACTIVE)
        .ifPresent(existing -> {
            existing.terminate();
            savingsPlanRepository.save(existing);
        });

    LocalDateTime nextScheduleAt = calculateNextScheduleAt(request);

    SavingsPlan plan = SavingsPlan.builder()
        .user(userPortfolio.getUser())
        .userPortfolio(userPortfolio)
        .status(SavingsStatus.ACTIVE)
        .amount(request.getAmount())
        .amountType(request.getAmountType())
        .salaryRatio(request.getSalaryRatio())
        .frequency(request.getFrequency())
        .dayOfMonth(request.getDayOfMonth())
        .dayOfWeek(request.getDayOfWeek())
        .purchaseMethod(request.getPurchaseMethod())
        .dcaCount(request.getDcaCount() != null ? request.getDcaCount() : 4)
        .nextScheduleAt(nextScheduleAt)
        .build();

    return SavingsPlanResponse.of(savingsPlanRepository.save(plan));
}
```

---

## BT-14-06 | next_schedule_at 계산 로직 (핵심)

```java
private LocalDateTime calculateNextScheduleAt(SavingsPlanRequest request) {
    LocalDate today = LocalDate.now();
    LocalDate nextDate;

    switch (request.getFrequency()) {
        case MONTHLY -> {
            int day = request.getDayOfMonth();
            // 이번 달 적립일이 오늘 이후면 이번 달, 아니면 다음 달
            LocalDate thisMonth = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
            nextDate = thisMonth.isAfter(today) ? thisMonth
                     : thisMonth.plusMonths(1).withDayOfMonth(Math.min(day, thisMonth.plusMonths(1).lengthOfMonth()));
        }
        case BIMONTHLY -> {
            int day = request.getDayOfMonth();
            LocalDate thisMonth = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
            nextDate = thisMonth.isAfter(today) ? thisMonth
                     : thisMonth.plusMonths(2).withDayOfMonth(Math.min(day, thisMonth.plusMonths(2).lengthOfMonth()));
        }
        case WEEKLY -> {
            DayOfWeek targetDay = DayOfWeek.of(request.getDayOfWeek() == 0 ? 1 : request.getDayOfWeek() + 1);
            nextDate = today.with(TemporalAdjusters.nextOrSame(targetDay));
            if (nextDate.equals(today)) nextDate = nextDate.plusWeeks(1);
        }
        default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    return nextDate.atTime(9, 0); // 오전 9시 실행
}

// 적립 완료 후 다음 회차 계산
public LocalDateTime calculateNextAfterExecution(SavingsPlan plan) {
    LocalDateTime current = plan.getNextScheduleAt();
    return switch (plan.getFrequency()) {
        case MONTHLY   -> current.plusMonths(1);
        case BIMONTHLY -> current.plusMonths(2);
        case WEEKLY    -> current.plusWeeks(1);
    };
}
```

---

## BT-14-02 | 적립 설정 조회 API

**엔드포인트**: `GET /api/savings/plans/active`

```java
public SavingsPlanResponse getActivePlan(Long userId) {
    return savingsPlanRepository.findByUserIdAndStatus(userId, SavingsStatus.ACTIVE)
        .map(SavingsPlanResponse::of)
        .orElseThrow(() -> new BusinessException(ErrorCode.SAVINGS_PLAN_NOT_FOUND));
}
```

---

## BT-14-03 | 적립 설정 수정 API

**엔드포인트**: `PATCH /api/savings/plans/{id}`

```java
@Transactional
public SavingsPlanResponse updatePlan(Long userId, Long planId, SavingsPlanUpdateRequest request) {
    SavingsPlan plan = savingsPlanRepository.findById(planId)
        .filter(p -> p.getUser().getId().equals(userId))
        .orElseThrow(() -> new BusinessException(ErrorCode.SAVINGS_PLAN_NOT_FOUND));

    if (request.getAmount()         != null) plan.setAmount(request.getAmount());
    if (request.getDayOfMonth()     != null) {
        plan.setDayOfMonth(request.getDayOfMonth());
        plan.setNextScheduleAt(calculateNextScheduleAt(plan)); // 변경된 날짜 기준 재계산
    }
    if (request.getPurchaseMethod() != null) plan.setPurchaseMethod(request.getPurchaseMethod());

    return SavingsPlanResponse.of(savingsPlanRepository.save(plan));
}
```

---

## BT-14-04 | 이번 달 건너뛰기

**엔드포인트**: `POST /api/savings/plans/{id}/skip`

```java
@Transactional
public void skipOnce(Long userId, Long planId) {
    SavingsPlan plan = findOwnedPlan(userId, planId);
    // SKIPPED 실행 기록 생성
    savingsExecutionRepository.save(SavingsExecution.skipped(plan));
    // 다음 회차로 next_schedule_at 업데이트
    plan.setNextScheduleAt(calculateNextAfterExecution(plan));
    savingsPlanRepository.save(plan);
}
```

---

## BT-14-05 | 일시 중지 API

**엔드포인트**: `POST /api/savings/plans/{id}/pause`

```java
@Getter
public class PausePlanRequest {
    private String reason;
    private LocalDate pauseUntil; // null이면 수동 재개까지
}
```

```java
@Transactional
public void pausePlan(Long userId, Long planId, PausePlanRequest request) {
    SavingsPlan plan = findOwnedPlan(userId, planId);
    plan.pause(request.getReason(),
        request.getPauseUntil() != null ? request.getPauseUntil().atStartOfDay() : null);
    savingsPlanRepository.save(plan);
}

@Transactional
public void resumePlan(Long userId, Long planId) {
    SavingsPlan plan = findOwnedPlan(userId, planId);
    plan.resume();
    plan.setNextScheduleAt(calculateNextScheduleAt(plan));
    savingsPlanRepository.save(plan);
}

// SavingsPlan 엔티티
public void pause(String reason, LocalDateTime until) {
    this.status = SavingsStatus.PAUSED;
    this.pauseReason = reason;
    this.pauseUntil = until;
}

public void resume() {
    this.status = SavingsStatus.ACTIVE;
    this.pauseReason = null;
    this.pauseUntil = null;
}
```

---

## BT-14-07 | 단위 테스트

```java
class SavingsPlanServiceTest {

    @Test
    @DisplayName("MONTHLY 31일 선택 시 next_schedule_at → 말일로 처리")
    void calculateNextScheduleAt_monthly_lastDayOfMonth() {
        // 2월의 경우 28일로 자동 처리
        SavingsPlanRequest req = mockRequest(MONTHLY, 31, null);
        LocalDateTime next = service.calculateNextScheduleAt(req);
        assertThat(next.getDayOfMonth()).isLessThanOrEqualTo(next.toLocalDate().lengthOfMonth());
    }

    @Test
    @DisplayName("investmentDay 범위 초과(29) 시 400")
    void createPlan_invalidDay_throws400() {
        // Bean Validation으로 처리
    }

    @Test
    @DisplayName("비회원 → 401")
    void createPlan_unauthorized() {
        // Spring Security 통합 테스트에서 검증
    }
}
```

---

## 완료 체크리스트

- [ ] BT-14-01: `POST /api/savings/plans` — 저장, 기존 ACTIVE 자동 TERMINATED
- [ ] BT-14-02: `GET /api/savings/plans/active` — 조회
- [ ] BT-14-03: `PATCH /api/savings/plans/{id}` — 수정 + next_schedule_at 재계산
- [ ] BT-14-04: `POST /api/savings/plans/{id}/skip` — 이번 달 건너뛰기
- [ ] BT-14-05: `POST /api/savings/plans/{id}/pause` + `/resume` — 일시 중지/재개
- [ ] BT-14-06: `calculateNextScheduleAt` — MONTHLY/BIMONTHLY/WEEKLY 분기 처리, 말일 처리
- [ ] BT-14-07: 단위 테스트 통과

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
