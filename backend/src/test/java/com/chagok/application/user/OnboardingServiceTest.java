package com.chagok.application.user;

import com.chagok.domain.portfolio.PortfolioTemplate;
import com.chagok.domain.portfolio.PortfolioTemplateRepository;
import com.chagok.domain.user.GoalType;
import com.chagok.domain.user.InvestmentType;
import com.chagok.domain.user.User;
import com.chagok.domain.user.UserOnboarding;
import com.chagok.domain.user.UserOnboardingRepository;
import com.chagok.domain.user.UserRepository;
import com.chagok.presentation.common.exception.BusinessException;
import com.chagok.presentation.common.exception.ErrorCode;
import com.chagok.presentation.user.dto.OnboardingRequest;
import com.chagok.presentation.user.dto.OnboardingResponse;
import com.chagok.presentation.user.dto.OnboardingUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

	@InjectMocks
	private OnboardingService onboardingService;

	@Mock
	private UserOnboardingRepository userOnboardingRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PortfolioTemplateRepository portfolioTemplateRepository;

	@Test
	@DisplayName("중복 온보딩 시 ONBOARDING_ALREADY_COMPLETED 예외 발생")
	void should_throwOnboardingAlreadyCompletedException_when_duplicateOnboarding() {
		given(userOnboardingRepository.existsByUserId(1L)).willReturn(true);

		assertThatThrownBy(() -> onboardingService.saveOnboarding(1L, validRequest()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ONBOARDING_ALREADY_COMPLETED);

		verify(userOnboardingRepository, never()).save(any());
	}

	@Test
	@DisplayName("존재하지 않는 유저면 USER_NOT_FOUND 예외 발생")
	void should_throwUserNotFoundException_when_userDoesNotExist() {
		given(userOnboardingRepository.existsByUserId(1L)).willReturn(false);
		given(userRepository.findById(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> onboardingService.saveOnboarding(1L, validRequest()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
	}

	@Test
	@DisplayName("온보딩 완료 시 추천 포트폴리오 ID를 함께 반환한다")
	void should_returnRecommendedPortfolio_when_saveOnboardingSucceeds() {
		User user = withId(User.builder().email("test@test.com").nickname("진").build(), 1L);
		PortfolioTemplate template = mock(PortfolioTemplate.class);
		given(template.getId()).willReturn(2L);

		given(userOnboardingRepository.existsByUserId(1L)).willReturn(false);
		given(userRepository.findById(1L)).willReturn(Optional.of(user));
		given(userOnboardingRepository.save(any(UserOnboarding.class)))
			.willAnswer(invocation -> invocation.getArgument(0));
		given(portfolioTemplateRepository
			.findByInvestmentTypeAndActiveTrueOrderByDisplayOrderAsc(InvestmentType.BALANCED))
			.willReturn(List.of(template));

		OnboardingResponse response = onboardingService.saveOnboarding(1L, balancedRequest());

		assertThat(response.getRecommendedPortfolioId()).isEqualTo(2L);
		assertThat(response.getInvestmentType()).isEqualTo(InvestmentType.BALANCED);
	}

	@Test
	@DisplayName("추천할 포트폴리오가 하나도 없으면 IllegalStateException 발생")
	void should_throwIllegalStateException_when_noActivePortfolioForInvestmentType() {
		User user = withId(User.builder().email("test@test.com").nickname("진").build(), 1L);

		given(userOnboardingRepository.existsByUserId(1L)).willReturn(false);
		given(userRepository.findById(1L)).willReturn(Optional.of(user));
		given(userOnboardingRepository.save(any(UserOnboarding.class)))
			.willAnswer(invocation -> invocation.getArgument(0));
		given(portfolioTemplateRepository
			.findByInvestmentTypeAndActiveTrueOrderByDisplayOrderAsc(InvestmentType.BALANCED))
			.willReturn(List.of());

		assertThatThrownBy(() -> onboardingService.saveOnboarding(1L, balancedRequest()))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("온보딩 정보가 없으면 조회 시 ONBOARDING_NOT_FOUND 예외 발생")
	void should_throwOnboardingNotFoundException_when_getOnboardingCalledWithoutOnboarding() {
		given(userOnboardingRepository.findByUserId(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> onboardingService.getOnboarding(1L))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ONBOARDING_NOT_FOUND);
	}

	@Test
	@DisplayName("온보딩 조회 성공 시 추천 포트폴리오 ID 없이 반환한다")
	void should_returnOnboarding_when_getOnboardingSucceeds() {
		UserOnboarding onboarding = UserOnboarding.builder()
			.investmentType(InvestmentType.SAFE)
			.monthlyAmount(100000)
			.goalType(GoalType.FREE)
			.build();
		given(userOnboardingRepository.findByUserId(1L)).willReturn(Optional.of(onboarding));

		OnboardingResponse response = onboardingService.getOnboarding(1L);

		assertThat(response.getRecommendedPortfolioId()).isNull();
		assertThat(response.getInvestmentType()).isEqualTo(InvestmentType.SAFE);
	}

	@Test
	@DisplayName("온보딩 수정 시 null이 아닌 필드만 반영된다")
	void should_updateOnlyProvidedFields_when_updateOnboardingCalled() {
		UserOnboarding onboarding = UserOnboarding.builder()
			.investmentType(InvestmentType.SAFE)
			.monthlyAmount(100000)
			.goalType(GoalType.FREE)
			.build();
		given(userOnboardingRepository.findByUserId(1L)).willReturn(Optional.of(onboarding));

		OnboardingUpdateRequest request = new OnboardingUpdateRequest(
			InvestmentType.GROWTH, null, null, null, null, null);

		OnboardingResponse response = onboardingService.updateOnboarding(1L, request);

		assertThat(response.getInvestmentType()).isEqualTo(InvestmentType.GROWTH);
		assertThat(response.getMonthlyAmount()).isEqualTo(100000);
	}

	@Test
	@DisplayName("수정할 온보딩이 없으면 ONBOARDING_NOT_FOUND 예외 발생")
	void should_throwOnboardingNotFoundException_when_updateOnboardingCalledWithoutOnboarding() {
		given(userOnboardingRepository.findByUserId(1L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> onboardingService.updateOnboarding(1L, validUpdateRequest()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ONBOARDING_NOT_FOUND);
	}

	private OnboardingRequest validRequest() {
		return new OnboardingRequest(InvestmentType.SAFE, 100000, GoalType.FREE, null, null, null);
	}

	private OnboardingRequest balancedRequest() {
		return new OnboardingRequest(InvestmentType.BALANCED, 100000, GoalType.FREE, null, null, null);
	}

	private OnboardingUpdateRequest validUpdateRequest() {
		return new OnboardingUpdateRequest(InvestmentType.GROWTH, null, null, null, null, null);
	}

	private <T> T withId(T entity, Long id) {
		try {
			Field field = entity.getClass().getDeclaredField("id");
			field.setAccessible(true);
			field.set(entity, id);
			return entity;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
