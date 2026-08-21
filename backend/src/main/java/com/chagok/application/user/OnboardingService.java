package com.chagok.application.user;

import com.chagok.domain.portfolio.PortfolioTemplate;
import com.chagok.domain.portfolio.PortfolioTemplateRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

	private final UserOnboardingRepository userOnboardingRepository;
	private final UserRepository userRepository;
	private final PortfolioTemplateRepository portfolioTemplateRepository;

	@Transactional
	public OnboardingResponse saveOnboarding(Long userId, OnboardingRequest request) {
		if (userOnboardingRepository.existsByUserId(userId)) {
			throw new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
		}

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		UserOnboarding onboarding = UserOnboarding.builder()
			.user(user)
			.investmentType(request.getInvestmentType())
			.monthlyAmount(request.getMonthlyAmount())
			.goalType(request.getGoalType())
			.goalAmount(request.getGoalAmount())
			.goalPeriodMonths(toShort(request.getGoalPeriodMonths()))
			.salaryDay(toShort(request.getSalaryDay()))
			.build();

		userOnboardingRepository.save(onboarding);

		Long recommendedPortfolioId = findRecommendedPortfolioId(request.getInvestmentType());

		return OnboardingResponse.of(onboarding, recommendedPortfolioId);
	}

	@Transactional(readOnly = true)
	public OnboardingResponse getOnboarding(Long userId) {
		return OnboardingResponse.from(findByUserIdOrThrow(userId));
	}

	@Transactional
	public OnboardingResponse updateOnboarding(Long userId, OnboardingUpdateRequest request) {
		UserOnboarding onboarding = findByUserIdOrThrow(userId);
		onboarding.update(
			request.getInvestmentType(),
			request.getMonthlyAmount(),
			request.getGoalType(),
			request.getGoalAmount(),
			toShort(request.getGoalPeriodMonths()),
			toShort(request.getSalaryDay()));
		return OnboardingResponse.from(onboarding);
	}

	// DB 컬럼(SMALLINT)에 맞춰 Short로 저장하되, 요청 DTO는 API 계약상 Integer를 그대로 유지한다.
	private Short toShort(Integer value) {
		return value == null ? null : value.shortValue();
	}

	private UserOnboarding findByUserIdOrThrow(Long userId) {
		return userOnboardingRepository.findByUserId(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ONBOARDING_NOT_FOUND));
	}

	private Long findRecommendedPortfolioId(InvestmentType investmentType) {
		return portfolioTemplateRepository
			.findByInvestmentTypeAndActiveTrueOrderByDisplayOrderAsc(investmentType)
			.stream()
			.findFirst()
			.map(PortfolioTemplate::getId)
			.orElseThrow(() -> new IllegalStateException("추천 포트폴리오가 없습니다: " + investmentType));
	}
}
