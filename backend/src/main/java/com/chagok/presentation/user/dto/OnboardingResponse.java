package com.chagok.presentation.user.dto;

import com.chagok.domain.user.GoalType;
import com.chagok.domain.user.InvestmentType;
import com.chagok.domain.user.UserOnboarding;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OnboardingResponse {

	private final Long onboardingId;
	private final InvestmentType investmentType;
	private final Integer monthlyAmount;
	private final GoalType goalType;
	private final Integer goalAmount;
	private final Integer goalPeriodMonths;
	private final Integer salaryDay;
	private final LocalDateTime completedAt;
	private final Long recommendedPortfolioId;

	private OnboardingResponse(UserOnboarding onboarding, Long recommendedPortfolioId) {
		this.onboardingId = onboarding.getId();
		this.investmentType = onboarding.getInvestmentType();
		this.monthlyAmount = onboarding.getMonthlyAmount();
		this.goalType = onboarding.getGoalType();
		this.goalAmount = onboarding.getGoalAmount();
		this.goalPeriodMonths = toInteger(onboarding.getGoalPeriodMonths());
		this.salaryDay = toInteger(onboarding.getSalaryDay());
		this.completedAt = onboarding.getOnboardingCompletedAt();
		this.recommendedPortfolioId = recommendedPortfolioId;
	}

	/** 온보딩 저장 직후 — 추천 포트폴리오 ID를 포함해서 반환한다. */
	public static OnboardingResponse of(UserOnboarding onboarding, Long recommendedPortfolioId) {
		return new OnboardingResponse(onboarding, recommendedPortfolioId);
	}

	/** 조회/수정 — 추천 포트폴리오 재계산 없이 온보딩 데이터만 반환한다. */
	public static OnboardingResponse from(UserOnboarding onboarding) {
		return new OnboardingResponse(onboarding, null);
	}

	private static Integer toInteger(Short value) {
		return value == null ? null : value.intValue();
	}
}
