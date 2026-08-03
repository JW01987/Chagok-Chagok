# BT-15 | 대시보드

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-14 (적립 설정)
- **완료 기준**: 대시보드 조회 API 동작 (MVP는 계산된 더미/실적립 데이터 반환)

---

## BT-15-01 | 대시보드 조회 API

**엔드포인트**: `GET /api/dashboard`
**인증**: 필요

### Service

```java
@Service
@RequiredArgsConstructor
public class DashboardService {

    public DashboardResponse getDashboard(Long userId) {
        // 1. ACTIVE 적립 플랜 조회
        Optional<SavingsPlan> activePlan = savingsPlanRepository
            .findByUserIdAndStatus(userId, SavingsStatus.ACTIVE);

        // 2. 적립 이력 집계 (savings_executions)
        List<SavingsExecution> executions = savingsExecutionRepository
            .findByUserIdAndStatusOrderByScheduledAtDesc(userId, ExecutionStatus.SUCCESS);

        // 3. 온보딩 목표 정보
        Optional<UserOnboarding> onboarding = userOnboardingRepository.findByUserId(userId);

        // MVP: 실제 증권사 연동 전 — 계산 기반 수익률 (연수익률 × 경과 개월)
        long totalPrincipal = executions.stream()
            .mapToLong(SavingsExecution::getTotalAmount).sum();

        // 포트폴리오 예상 수익률 적용 (더미)
        double estimatedReturnRate = getEstimatedAnnualReturn(userId) / 100.0;
        long estimatedAsset = calculateEstimatedAsset(executions, estimatedReturnRate);
        long totalReturn = estimatedAsset - totalPrincipal;
        double returnRate = totalPrincipal > 0
            ? Math.round((double) totalReturn / totalPrincipal * 10000.0) / 100.0 : 0.0;

        // 4. 목표 달성률
        Double goalAchievementRate = null;
        if (onboarding.isPresent() && onboarding.get().getGoalAmount() != null) {
            goalAchievementRate = Math.round((double) totalPrincipal
                / onboarding.get().getGoalAmount() * 10000.0) / 100.0;
        }

        // 5. 월별 이력 (최근 12개월)
        List<MonthlyHistoryDto> monthlyHistory = buildMonthlyHistory(executions);

        // 6. 다음 적립 예정일
        LocalDateTime nextScheduleAt = activePlan.map(SavingsPlan::getNextScheduleAt).orElse(null);

        return DashboardResponse.builder()
            .totalPrincipal(totalPrincipal)
            .currentValue(estimatedAsset)
            .totalReturn(totalReturn)
            .returnRate(returnRate)
            .goalAchievementRate(goalAchievementRate)
            .nextScheduleAt(nextScheduleAt)
            .activePlanAmount(activePlan.map(SavingsPlan::getAmount).orElse(null))
            .monthlyHistory(monthlyHistory)
            .build();
    }

    private List<MonthlyHistoryDto> buildMonthlyHistory(List<SavingsExecution> executions) {
        return executions.stream()
            .filter(e -> e.getScheduledAt().isAfter(LocalDateTime.now().minusMonths(12)))
            .map(e -> MonthlyHistoryDto.builder()
                .month(e.getScheduledAt().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .amount(e.getTotalAmount())
                .status(e.getStatus().name())
                .build())
            .toList();
    }

    // 가중평균 복리 계산 (실제 매수일 기반)
    private long calculateEstimatedAsset(List<SavingsExecution> executions, double annualRate) {
        if (executions.isEmpty()) return 0L;
        double monthlyRate = annualRate / 12.0;
        long now = System.currentTimeMillis();
        double total = 0;
        for (SavingsExecution e : executions) {
            long ms = now - e.getScheduledAt().toInstant(ZoneOffset.UTC).toEpochMilli();
            double months = ms / (1000.0 * 60 * 60 * 24 * 30.44);
            total += e.getTotalAmount() * Math.pow(1 + monthlyRate, months);
        }
        return Math.round(total);
    }
}
```

---

## BT-15-02 | Response DTO

```java
@Getter @Builder
public class DashboardResponse {
    private Long totalPrincipal;          // 총 투자 원금
    private Long currentValue;            // 현재 평가금액
    private Long totalReturn;             // 총 수익
    private Double returnRate;            // 수익률 (%)
    private Double goalAchievementRate;   // 목표 달성률 (%, nullable)
    private LocalDateTime nextScheduleAt; // 다음 적립 예정일 (nullable)
    private Integer activePlanAmount;     // 이번 달 적립 예정 금액 (nullable)
    private List<MonthlyHistoryDto> monthlyHistory; // 최근 12개월 이력

    @Getter @Builder
    public static class MonthlyHistoryDto {
        private String month;             // "2026-05"
        private Long amount;              // 적립 금액
        private String status;            // SUCCESS | FAILED | SKIPPED
    }
}
```

---

## BT-15-03 | 목표 달성률 계산

```java
// 온보딩에 목표 금액이 있는 경우만 계산
private Double calculateGoalAchievementRate(Long totalPrincipal, UserOnboarding onboarding) {
    if (onboarding == null || onboarding.getGoalAmount() == null) return null;
    return Math.min(100.0,
        Math.round((double) totalPrincipal / onboarding.getGoalAmount() * 10000.0) / 100.0);
}
```

---

## BT-15-04 | 포트폴리오 목표 비율 vs 현재 비율 응답

```java
// Beta에서 실제 데이터로 교체 예정 — MVP는 목표 비율만 반환
@GetMapping("/portfolio-ratio")
public ResponseEntity<ApiResponse<PortfolioRatioResponse>> getPortfolioRatio(
        @AuthenticationPrincipal Long userId) {
    UserPortfolio active = userPortfolioRepository
        .findByUserIdAndStatus(userId, PortfolioStatus.ACTIVE)
        .orElseThrow();
    List<AllocationDto> target = portfolioAllocationRepository
        .findByPortfolioTemplateIdWithEtf(active.getPortfolioTemplate().getId());
    // MVP: 현재 비율 = 목표 비율 (더미)
    return ResponseEntity.ok(ApiResponse.ok(
        PortfolioRatioResponse.mvpDummy(target)));
}
```

---

## 완료 체크리스트

- [ ] BT-15-01: `GET /api/dashboard` — 원금/평가금액/수익률/다음 적립일 반환
- [ ] BT-15-02: 월별 적립 이력 (최근 12개월)
- [ ] BT-15-03: 목표 달성률 계산
- [ ] BT-15-04: 포트폴리오 목표 비율 응답 (MVP는 더미)
- [ ] 적립 이력 없는 유저 → 빈 데이터 반환 (404 아님)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
