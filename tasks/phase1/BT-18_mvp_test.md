# BT-18 | MVP 테스트 & Swagger 문서화

- **최초 작성일**: 2026-08-03
- **업데이트**: 2026-08-03
- **Phase**: 1 — MVP
- **상태**: ⬜ 대기
- **선행 태스크**: BT-07 ~ BT-17 (모든 Phase 1 API)
- **완료 기준**: 단위 테스트 50% 커버리지 + E2E 통합 테스트 통과 + Swagger 정리

---

## BT-18-01 | 단위 테스트 목표 (50% 커버리지)

### 우선순위 테스트 대상

| 서비스 | 핵심 케이스 |
|--------|------------|
| AuthService | 회원가입/로그인/재발급/로그아웃/5회 잠금 |
| SimulationService | 복리 계산 정확도, 목표 역산 |
| SavingsPlanService | next_schedule_at (말일 처리, 주간, 격월) |
| PortfolioService | 목록 필터링, 존재하지 않는 ID |
| UserProfileService | 닉네임 중복, 탈퇴 후 이메일 익명화 |

### 테스트 설정

```java
// src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: true   # 마이그레이션 스크립트 테스트 환경에서도 실행
```

```java
// build.gradle — JaCoCo 커버리지 설정
jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                '**/dto/**',
                '**/config/**',
                '**/exception/**',
                '**/*Application*'
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.50  // 50% 이상
            }
        }
    }
}
```

---

## BT-18-02 | E2E 통합 테스트 (회원가입 → 대시보드 전 흐름)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MvpIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    // 시나리오: 회원가입 → 로그인 → 온보딩 → 포트폴리오 선택 → 적립 설정 → 대시보드 조회
    @Test
    @DisplayName("MVP 전체 플로우 통합 테스트")
    void fullFlow() throws Exception {
        // 1. 회원가입
        String signupBody = om.writeValueAsString(Map.of(
            "email", "test@chagok.io",
            "password", "Test1234!",
            "nickname", "테스터"
        ));
        MvcResult signupResult = mvc.perform(post("/api/auth/signup")
                .contentType(APPLICATION_JSON).content(signupBody))
            .andExpect(status().isCreated())
            .andReturn();

        String accessToken = extractToken(signupResult, "accessToken");

        // 2. 온보딩 저장
        String onboardingBody = om.writeValueAsString(Map.of(
            "investmentType", "BALANCED",
            "monthlyAmount", 300000,
            "goalType", "HOUSING",
            "goalAmount", 100000000,
            "goalPeriodMonths", 60
        ));
        mvc.perform(post("/api/onboarding")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON).content(onboardingBody))
            .andExpect(status().isCreated());

        // 3. 포트폴리오 목록 조회 (게스트도 가능)
        MvcResult portfolioList = mvc.perform(get("/api/portfolios"))
            .andExpect(status().isOk())
            .andReturn();

        Long portfolioId = extractFirstPortfolioId(portfolioList);

        // 4. 포트폴리오 선택
        mvc.perform(post("/api/user-portfolios")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("portfolioTemplateId", portfolioId))))
            .andExpect(status().isCreated());

        // 5. 적립 설정
        mvc.perform(post("/api/savings/plans")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "userPortfolioId", extractUserPortfolioId(accessToken),
                    "amount", 300000,
                    "amountType", "FIXED",
                    "frequency", "MONTHLY",
                    "dayOfMonth", 25,
                    "purchaseMethod", "LUMP_SUM"
                ))))
            .andExpect(status().isCreated());

        // 6. 대시보드 조회 (적립 이력 없는 초기 상태)
        mvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalPrincipal").value(0))
            .andExpect(jsonPath("$.data.nextScheduleAt").isNotEmpty());
    }

    // 토큰 파서 헬퍼
    private String extractToken(MvcResult result, String field) throws Exception {
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        return root.path("data").path(field).asText();
    }
}
```

---

## BT-18-03 | 주요 엣지 케이스 테스트

```java
// 인증
@Test void signup_duplicateEmail_returns409() {}
@Test void login_wrongPassword_returns401() {}
@Test void login_5thFail_accountLocked() {}
@Test void reissue_expiredRefreshToken_returns401() {}

// 포트폴리오
@Test void getPortfolio_notFound_returns404() {}
@Test void compare_moreThan2Ids_returns400() {}

// 적립 설정
@Test void createPlan_dayOfMonth29_adjustedToLastDay() {}
@Test void skipPlan_advancesNextScheduleAt() {}
@Test void pausePlan_doesNotExecute() {}

// 대시보드
@Test void getDashboard_noExecutions_returnsZeroPrincipal() {}
@Test void getDashboard_unauthenticated_returns401() {}

// 시뮬레이션 (게스트)
@Test void simulate_monthlyAmount0_returns400() {}
@Test void reverseSimulate_targetAmount30M_36months_9pct() {}
```

---

## BT-18-04 | Swagger (SpringDoc OpenAPI) 정리

```java
// SwaggerConfig.java (BT-02에서 초기 생성 → MVP 단계에서 정리)
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("차곡차곡 API")
            .description("사회초년생 적립식 투자 앱 — MVP v1.0")
            .version("v1.0.0"))
        .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
        .components(new Components()
            .addSecuritySchemes("BearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
}
```

### API 그룹별 태그 확인 (각 Controller에 @Tag 적용 확인)

| 태그 | 컨트롤러 | 엔드포인트 수 |
|------|---------|------------|
| Auth | AuthController | 5 (signup/login/reissue/logout/check) |
| OAuth | OAuthController | 2 (kakao/apple) |
| Onboarding | OnboardingController | 3 (save/get/update) |
| Portfolio | PortfolioController | 3 (list/detail/compare) |
| UserPortfolio | UserPortfolioController | 3 (select/active/terminate) |
| Simulation | SimulationController | 2 (simulate/reverse) |
| SavingsPlan | SavingsPlanController | 6 (create/active/update/skip/pause/resume) |
| Dashboard | DashboardController | 2 (dashboard/portfolio-ratio) |
| UserProfile | UserProfileController | 3 (get/update/withdraw) |
| NotificationSettings | NotificationSettingsController | 3 (get/update/fcm-token) |

### Swagger 접근 URL (로컬)

- UI: `http://localhost:8080/swagger-ui.html`
- JSON: `http://localhost:8080/v3/api-docs`

---

## BT-18-05 | CI 파이프라인 커버리지 연동

```yaml
# .github/workflows/ci.yml — backend-ci job에 추가
- name: Run tests with coverage
  run: ./gradlew test jacocoTestReport jacocoTestCoverageVerification

- name: Upload coverage report
  uses: actions/upload-artifact@v4
  with:
    name: jacoco-report
    path: build/reports/jacoco/test/html/
```

---

## 완료 체크리스트

- [ ] BT-18-01: JaCoCo 설정 + 50% 커버리지 달성
- [ ] BT-18-02: E2E 통합 테스트 전 흐름 통과
- [ ] BT-18-03: 엣지 케이스 테스트 (인증/포트폴리오/적립/대시보드)
- [ ] BT-18-04: Swagger UI 정상 접근 + 각 API 태그 정리
- [ ] BT-18-05: CI에서 커버리지 미달 시 빌드 실패

---

_최초 작성: 2026-08-03 | 업데이트: 2026-08-03_
