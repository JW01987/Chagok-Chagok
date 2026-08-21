package com.chagok.presentation.user;

import com.chagok.application.user.OnboardingService;
import com.chagok.domain.user.GoalType;
import com.chagok.domain.user.InvestmentType;
import com.chagok.infrastructure.security.JwtAuthenticationFilter;
import com.chagok.infrastructure.security.JwtTokenProvider;
import com.chagok.infrastructure.security.SecurityConfig;
import com.chagok.infrastructure.security.oauth2.CustomOAuth2UserService;
import com.chagok.infrastructure.security.oauth2.OAuth2SuccessHandler;
import com.chagok.presentation.common.exception.BusinessException;
import com.chagok.presentation.common.exception.ErrorCode;
import com.chagok.presentation.user.dto.OnboardingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private OnboardingService onboardingService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private CustomOAuth2UserService customOAuth2UserService;

	@MockitoBean
	private OAuth2SuccessHandler oAuth2SuccessHandler;

	@Test
	@DisplayName("인증 없이 온보딩 저장을 호출하면 403을 반환한다")
	void should_return403_when_saveOnboardingCalledWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/users/onboarding")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validPayload())))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("인증된 유저가 온보딩을 저장하면 201과 추천 포트폴리오를 반환한다")
	void should_return201WithRecommendedPortfolio_when_saveOnboardingSucceeds() throws Exception {
		authenticateAs(1L);
		given(onboardingService.saveOnboarding(eq(1L), any()))
			.willReturn(onboardingResponse(2L));

		mockMvc.perform(post("/api/users/onboarding")
				.header("Authorization", "Bearer valid-token")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validPayload())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.recommendedPortfolioId").value(2));
	}

	@Test
	@DisplayName("월 투자 금액이 50,000원 미만이면 400을 반환한다")
	void should_return400_when_saveOnboardingAmountTooLow() throws Exception {
		authenticateAs(1L);

		mockMvc.perform(post("/api/users/onboarding")
				.header("Authorization", "Bearer valid-token")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(
					new OnboardingPayload(InvestmentType.SAFE, 10000, GoalType.FREE, null, null, null))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	@DisplayName("이미 온보딩을 완료했으면 409를 반환한다")
	void should_return409_when_saveOnboardingAlreadyCompleted() throws Exception {
		authenticateAs(1L);
		given(onboardingService.saveOnboarding(eq(1L), any()))
			.willThrow(new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED));

		mockMvc.perform(post("/api/users/onboarding")
				.header("Authorization", "Bearer valid-token")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validPayload())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("USER_001"));
	}

	@Test
	@DisplayName("온보딩 조회 성공 시 200을 반환한다")
	void should_return200_when_getOnboardingSucceeds() throws Exception {
		authenticateAs(1L);
		given(onboardingService.getOnboarding(1L)).willReturn(onboardingResponse(null));

		mockMvc.perform(get("/api/users/onboarding")
				.header("Authorization", "Bearer valid-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.investmentType").value("BALANCED"));
	}

	@Test
	@DisplayName("온보딩 정보가 없으면 조회 시 404를 반환한다")
	void should_return404_when_getOnboardingNotFound() throws Exception {
		authenticateAs(1L);
		given(onboardingService.getOnboarding(1L))
			.willThrow(new BusinessException(ErrorCode.ONBOARDING_NOT_FOUND));

		mockMvc.perform(get("/api/users/onboarding")
				.header("Authorization", "Bearer valid-token"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("USER_002"));
	}

	@Test
	@DisplayName("온보딩 수정 성공 시 200을 반환한다")
	void should_return200_when_updateOnboardingSucceeds() throws Exception {
		authenticateAs(1L);
		given(onboardingService.updateOnboarding(eq(1L), any())).willReturn(onboardingResponse(null));

		mockMvc.perform(patch("/api/users/onboarding")
				.header("Authorization", "Bearer valid-token")
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(
					new OnboardingUpdatePayload(InvestmentType.GROWTH, null, null, null, null, null))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));
	}

	private void authenticateAs(Long userId) {
		given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
		given(jwtTokenProvider.getUserId("valid-token")).willReturn(userId);
	}

	private OnboardingPayload validPayload() {
		return new OnboardingPayload(InvestmentType.BALANCED, 100000, GoalType.FREE, null, null, null);
	}

	private OnboardingResponse onboardingResponse(Long recommendedPortfolioId) {
		return OnboardingResponse.of(
			com.chagok.domain.user.UserOnboarding.builder()
				.investmentType(InvestmentType.BALANCED)
				.monthlyAmount(100000)
				.goalType(GoalType.FREE)
				.build(),
			recommendedPortfolioId);
	}

	private record OnboardingPayload(InvestmentType investmentType, Integer monthlyAmount, GoalType goalType,
			Integer goalAmount, Integer goalPeriodMonths, Integer salaryDay) {
	}

	private record OnboardingUpdatePayload(InvestmentType investmentType, Integer monthlyAmount, GoalType goalType,
			Integer goalAmount, Integer goalPeriodMonths, Integer salaryDay) {
	}
}
