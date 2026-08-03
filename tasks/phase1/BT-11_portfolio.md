# BT-11 | 포트폴리오

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-07 (DB 마이그레이션)
- **완료 기준**: 포트폴리오 목록/상세/비교 API 동작 + 게스트 허용

---

## BT-11-01 | 포트폴리오 목록 조회 API

**엔드포인트**: `GET /api/portfolios?riskType=BALANCED`
**인증**: 불필요 (게스트 허용)

### Repository

```java
public interface PortfolioTemplateRepository extends JpaRepository<PortfolioTemplate, Long> {
    List<PortfolioTemplate> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<PortfolioTemplate> findByInvestmentTypeAndIsActiveTrueOrderByDisplayOrderAsc(InvestmentType type);
}
```

### Service

```java
public List<PortfolioListResponse> getPortfolios(InvestmentType riskType) {
    List<PortfolioTemplate> templates = (riskType != null)
        ? portfolioTemplateRepository.findByInvestmentTypeAndIsActiveTrueOrderByDisplayOrderAsc(riskType)
        : portfolioTemplateRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

    return templates.stream()
        .map(t -> {
            List<AllocationDto> allocations = portfolioAllocationRepository
                .findByPortfolioTemplateIdWithEtf(t.getId());
            return PortfolioListResponse.of(t, allocations);
        })
        .toList();
}
```

### Response DTO

```java
@Getter @Builder
public class PortfolioListResponse {
    private Long portfolioId;
    private String code;
    private String name;
    private String description;
    private String investmentType;
    private Double expectedReturnMin;
    private Double expectedReturnMax;
    private List<AllocationDto> composition;

    @Getter @Builder
    public static class AllocationDto {
        private String ticker;
        private String etfName;
        private String assetClass;
        private Double targetRatio;
    }
}
```

### Controller

```java
@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "포트폴리오 API")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    @Operation(summary = "포트폴리오 목록 조회 (게스트 허용)")
    public ResponseEntity<ApiResponse<List<PortfolioListResponse>>> getPortfolios(
            @RequestParam(required = false) InvestmentType riskType) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.getPortfolios(riskType)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "포트폴리오 상세 조회 (게스트 허용)")
    public ResponseEntity<ApiResponse<PortfolioDetailResponse>> getPortfolioDetail(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.getPortfolioDetail(id)));
    }

    @GetMapping("/compare")
    @Operation(summary = "포트폴리오 비교 (최대 2개)")
    public ResponseEntity<ApiResponse<List<PortfolioDetailResponse>>> compare(
            @RequestParam List<Long> ids) {
        if (ids.size() > 2) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.comparePortfolios(ids)));
    }
}
```

---

## BT-11-02 | 포트폴리오 상세 조회 API

**엔드포인트**: `GET /api/portfolios/{id}`

### Service

```java
public PortfolioDetailResponse getPortfolioDetail(Long id) {
    PortfolioTemplate template = portfolioTemplateRepository.findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

    List<AllocationDto> allocations = portfolioAllocationRepository
        .findByPortfolioTemplateIdWithEtf(id);

    // 백테스트: 최근 10년 연도별 수익률 집계
    List<BacktestYearlyDto> backtest = backtestReturnRepository
        .findYearlyReturnsByPortfolioId(id);

    return PortfolioDetailResponse.of(template, allocations, backtest);
}
```

### 백테스트 연도별 집계 쿼리

```java
// BacktestReturnRepository
@Query("""
    SELECT SUBSTRING(b.yearMonth, 1, 4) as year,
           SUM(b.returnRate) as totalReturn
    FROM BacktestReturn b
    WHERE b.portfolioTemplate.id = :portfolioId
      AND b.yearMonth >= :tenYearsAgo
    GROUP BY SUBSTRING(b.yearMonth, 1, 4)
    ORDER BY year ASC
    """)
List<BacktestYearlyDto> findYearlyReturnsByPortfolioId(
    @Param("portfolioId") Long portfolioId,
    @Param("tenYearsAgo") String tenYearsAgo  // "2016-01"
);
```

### Response DTO

```java
@Getter @Builder
public class PortfolioDetailResponse {
    private Long portfolioId;
    private String code;
    private String name;
    private String description;
    private String investmentType;
    private Double expectedReturnMin;
    private Double expectedReturnMax;
    private Double mdd;
    private Double volatility;
    private Double rebalanceThreshold;
    private List<AllocationDto> composition;       // 도넛 차트용
    private List<BacktestYearlyDto> backtestData;  // 10년 수익률
}
```

---

## BT-11-03 | 포트폴리오 비교 API

**엔드포인트**: `GET /api/portfolios/compare?ids=1,2`

```java
public List<PortfolioDetailResponse> comparePortfolios(List<Long> ids) {
    return ids.stream()
        .map(this::getPortfolioDetail)
        .toList();
}
```

---

## BT-11-04 | Fetch Join 최적화 (N+1 방지)

```java
// PortfolioAllocationRepository
@Query("""
    SELECT pa FROM PortfolioAllocation pa
    JOIN FETCH pa.etf
    WHERE pa.portfolioTemplate.id = :portfolioId
    ORDER BY pa.displayOrder ASC
    """)
List<PortfolioAllocation> findByPortfolioTemplateIdWithEtf(@Param("portfolioId") Long portfolioId);
```

---

## BT-11-05 | 단위 테스트

```java
@Test
@DisplayName("존재하지 않는 포트폴리오 ID 조회 시 404")
void getPortfolioDetail_notFound_throws404() {
    given(portfolioTemplateRepository.findById(999L)).willReturn(Optional.empty());
    assertThatThrownBy(() -> portfolioService.getPortfolioDetail(999L))
        .isInstanceOf(BusinessException.class);
}

@Test
@DisplayName("riskType 필터링 시 해당 성향만 반환")
void getPortfolios_filterByRiskType_returnsFiltered() {
    given(portfolioTemplateRepository.findByInvestmentTypeAndIsActiveTrueOrderByDisplayOrderAsc(BALANCED))
        .willReturn(List.of(balancedPortfolio()));
    List<PortfolioListResponse> result = portfolioService.getPortfolios(BALANCED);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getInvestmentType()).isEqualTo("BALANCED");
}
```

---

## 완료 체크리스트

- [ ] BT-11-01: `GET /api/portfolios` — riskType 필터링, 게스트 허용
- [ ] BT-11-02: `GET /api/portfolios/{id}` — ETF 목록 + 10년 백테스트
- [ ] BT-11-03: `GET /api/portfolios/compare?ids=1,2`
- [ ] BT-11-04: Fetch Join으로 N+1 쿼리 방지
- [ ] BT-11-05: 단위 테스트 통과

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
