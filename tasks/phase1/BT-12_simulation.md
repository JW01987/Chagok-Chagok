# BT-12 | 수익 시뮬레이션

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-02 (백엔드 초기화) — DB 불필요
- **완료 기준**: 복리 계산 API + 목표 역산 API 동작 + 단위 테스트

---

## 개요

DB 조회 없이 순수 계산 로직으로 구현하는 API.
게스트도 사용 가능하며, 월별 복리 계산 / 목표 역산 / 인플레이션 반영을 지원한다.

---

## BT-12-01 | 시뮬레이션 계산 API

**엔드포인트**: `POST /api/simulation`
**인증**: 불필요 (게스트 허용)

### Request DTO

```java
@Getter
public class SimulationRequest {

    @NotNull @Min(10000)
    private Integer monthlyAmount;     // 월 적립금

    @NotNull @DecimalMin("0.1") @DecimalMax("30.0")
    private Double annualReturnRate;   // 예상 연수익률 (%)

    @NotNull @Min(1) @Max(600)
    private Integer months;            // 투자 기간 (개월)

    private Double inflationRate;      // 인플레이션 반영 (nullable, 고급 옵션)

    private Double annualSalaryGrowthRate; // 연봉 인상률 시나리오 (nullable)
}
```

### 계산 서비스

```java
@Service
public class SimulationService {

    /**
     * 복리 적립식 계산
     * FV = PMT × [(1+r)^n - 1] / r
     * r = 월 수익률, n = 개월 수, PMT = 월 적립금
     */
    public SimulationResponse calculate(SimulationRequest request) {
        double monthlyRate = request.getAnnualReturnRate() / 100.0 / 12.0;
        int n = request.getMonths();
        int pmt = request.getMonthlyAmount();

        List<MonthlyDataPoint> monthlyData = new ArrayList<>();
        long totalPrincipal = 0;
        double totalAsset = 0;

        for (int month = 1; month <= n; month++) {
            // 월급 인상 시나리오 반영
            int currentPmt = pmt;
            if (request.getAnnualSalaryGrowthRate() != null && month > 1 && (month - 1) % 12 == 0) {
                pmt = (int)(pmt * (1 + request.getAnnualSalaryGrowthRate() / 100.0));
                currentPmt = pmt;
            }

            totalPrincipal += currentPmt;
            totalAsset = (totalAsset + currentPmt) * (1 + monthlyRate);

            monthlyData.add(MonthlyDataPoint.builder()
                .month(month)
                .principal(totalPrincipal)
                .asset((long) Math.round(totalAsset))
                .returnAmount((long) Math.round(totalAsset - totalPrincipal))
                .returnRate(totalPrincipal > 0
                    ? Math.round((totalAsset - totalPrincipal) / totalPrincipal * 10000.0) / 100.0
                    : 0.0)
                .build());
        }

        long finalAsset = (long) Math.round(totalAsset);
        long totalReturn = finalAsset - totalPrincipal;
        double returnRate = totalPrincipal > 0
            ? Math.round((double) totalReturn / totalPrincipal * 10000.0) / 100.0
            : 0.0;

        // 인플레이션 반영 실질 수익률
        Double realReturnRate = null;
        if (request.getInflationRate() != null) {
            double nominalRate = request.getAnnualReturnRate() / 100.0;
            double inflationRate = request.getInflationRate() / 100.0;
            realReturnRate = Math.round((nominalRate - inflationRate) / (1 + inflationRate) * 10000.0) / 100.0;
        }

        return SimulationResponse.builder()
            .totalPrincipal(totalPrincipal)
            .totalAsset(finalAsset)
            .totalReturn(totalReturn)
            .returnRate(returnRate)
            .realReturnRate(realReturnRate)
            .monthlyData(monthlyData)
            // 비교 카드
            .comparisonCard(buildComparisonCard(finalAsset))
            .build();
    }

    private ComparisonCard buildComparisonCard(long finalAsset) {
        // 서울 전세 평균 (더미 — Beta에서 실시간 데이터로 교체)
        long seoulAverageJeonse = 450_000_000L;
        return ComparisonCard.builder()
            .seoulJeonseAverage(seoulAverageJeonse)
            .achievementRate(Math.round((double) finalAsset / seoulAverageJeonse * 10000.0) / 100.0)
            .build();
    }
}
```

### Response DTO

```java
@Getter @Builder
public class SimulationResponse {
    private Long totalPrincipal;    // 총 투자 원금
    private Long totalAsset;        // 예상 자산
    private Long totalReturn;       // 총 수익
    private Double returnRate;      // 수익률 (%)
    private Double realReturnRate;  // 실질 수익률 (인플레이션 반영, nullable)
    private List<MonthlyDataPoint> monthlyData;
    private ComparisonCard comparisonCard;

    @Getter @Builder
    public static class MonthlyDataPoint {
        private Integer month;
        private Long principal;
        private Long asset;
        private Long returnAmount;
        private Double returnRate;
    }

    @Getter @Builder
    public static class ComparisonCard {
        private Long seoulJeonseAverage;
        private Double achievementRate;  // 서울 전세 대비 달성률 (%)
    }
}
```

---

## BT-12-02 | 목표 역산 API

**엔드포인트**: `POST /api/simulation/reverse`
**목적**: "N년 후 N원을 모으려면 매달 얼마?"

### Request / Response

```java
@Getter
public class ReverseSimulationRequest {
    @NotNull @Min(1000000)
    private Long targetAmount;        // 목표 금액

    @NotNull @Min(1) @Max(600)
    private Integer months;           // 목표 기간 (개월)

    @NotNull @DecimalMin("0.1")
    private Double annualReturnRate;  // 예상 연수익률
}
```

```java
// PMT = FV × r / [(1+r)^n - 1]
public ReverseSimulationResponse calculateReverse(ReverseSimulationRequest request) {
    double r = request.getAnnualReturnRate() / 100.0 / 12.0;
    int n = request.getMonths();
    long fv = request.getTargetAmount();

    long monthlyAmount;
    if (r == 0) {
        monthlyAmount = (long) Math.ceil((double) fv / n);
    } else {
        double denominator = (Math.pow(1 + r, n) - 1) / r;
        monthlyAmount = (long) Math.ceil(fv / denominator);
    }

    // 10원 단위 반올림 (UX)
    monthlyAmount = (long)(Math.ceil(monthlyAmount / 10.0) * 10);

    return ReverseSimulationResponse.builder()
        .targetAmount(fv)
        .months(n)
        .annualReturnRate(request.getAnnualReturnRate())
        .requiredMonthlyAmount(monthlyAmount)
        .build();
}
```

---

## BT-12-03 | 컨트롤러

```java
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "수익 시뮬레이션 API")
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping
    @Operation(summary = "수익 시뮬레이션 (게스트 허용)")
    public ResponseEntity<ApiResponse<SimulationResponse>> simulate(
            @Valid @RequestBody SimulationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(simulationService.calculate(request)));
    }

    @PostMapping("/reverse")
    @Operation(summary = "목표 역산 — 매달 얼마 필요? (게스트 허용)")
    public ResponseEntity<ApiResponse<ReverseSimulationResponse>> reverse(
            @Valid @RequestBody ReverseSimulationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(simulationService.calculateReverse(request)));
    }
}
```

---

## BT-12-04 | 단위 테스트

```java
class SimulationServiceTest {

    SimulationService service = new SimulationService();

    @Test
    @DisplayName("월 20만원, 연 9%, 36개월 복리 계산")
    void calculate_basic() {
        SimulationRequest req = new SimulationRequest(200_000, 9.0, 36, null, null);
        SimulationResponse res = service.calculate(req);

        assertThat(res.getTotalPrincipal()).isEqualTo(7_200_000L);
        assertThat(res.getTotalAsset()).isGreaterThan(7_200_000L);
        assertThat(res.getMonthlyData()).hasSize(36);
    }

    @Test
    @DisplayName("투자 기간 0 이하 → 400 (Bean Validation)")
    void calculate_invalidMonths() {
        // Controller 레이어에서 @Valid로 처리
    }

    @Test
    @DisplayName("목표 역산 — 3,000만원, 36개월, 연9%")
    void reverse_targetAmount() {
        ReverseSimulationRequest req = new ReverseSimulationRequest(30_000_000L, 36, 9.0);
        ReverseSimulationResponse res = service.calculateReverse(req);

        // 약 77만원대 나와야 함
        assertThat(res.getRequiredMonthlyAmount()).isBetween(760_000L, 800_000L);
    }

    @Test
    @DisplayName("인플레이션 2% 반영 시 실질 수익률 계산")
    void calculate_withInflation() {
        SimulationRequest req = new SimulationRequest(200_000, 9.0, 36, 2.0, null);
        SimulationResponse res = service.calculate(req);
        assertThat(res.getRealReturnRate()).isNotNull().isGreaterThan(0);
    }
}
```

---

## 완료 체크리스트

- [ ] BT-12-01: `POST /api/simulation` — 복리 계산 + 월별 데이터 + 서울 전세 비교 카드
- [ ] BT-12-02: `POST /api/simulation/reverse` — 목표 역산 (10원 단위 반올림)
- [ ] BT-12-03: 컨트롤러 + 게스트 허용 확인
- [ ] BT-12-04: 단위 테스트 (복리 정확도, 목표 역산 값 범위)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
