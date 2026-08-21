package com.chagok.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserOnboardingTest {

	@Test
	@DisplayName("생성 시 온보딩 완료 시각이 즉시 채워진다")
	void should_setCompletedAt_when_created() {
		UserOnboarding onboarding = UserOnboarding.builder()
			.investmentType(InvestmentType.BALANCED)
			.monthlyAmount(100000)
			.goalType(GoalType.FREE)
			.build();

		assertThat(onboarding.getOnboardingCompletedAt()).isNotNull();
	}

	@Test
	@DisplayName("null이 아닌 필드만 갱신된다 (PATCH 방식)")
	void should_updateOnlyNonNullFields_when_updateCalled() {
		UserOnboarding onboarding = UserOnboarding.builder()
			.investmentType(InvestmentType.SAFE)
			.monthlyAmount(100000)
			.goalType(GoalType.FREE)
			.goalAmount(1000000)
			.goalPeriodMonths((short) 12)
			.salaryDay((short) 25)
			.build();

		onboarding.update(InvestmentType.GROWTH, null, null, null, null, null);

		assertThat(onboarding.getInvestmentType()).isEqualTo(InvestmentType.GROWTH);
		assertThat(onboarding.getMonthlyAmount()).isEqualTo(100000);
		assertThat(onboarding.getGoalType()).isEqualTo(GoalType.FREE);
		assertThat(onboarding.getGoalAmount()).isEqualTo(1000000);
		assertThat(onboarding.getGoalPeriodMonths()).isEqualTo((short) 12);
		assertThat(onboarding.getSalaryDay()).isEqualTo((short) 25);
	}

	@Test
	@DisplayName("모든 필드를 전달하면 전부 갱신된다")
	void should_updateAllFields_when_allValuesProvided() {
		UserOnboarding onboarding = UserOnboarding.builder()
			.investmentType(InvestmentType.SAFE)
			.monthlyAmount(100000)
			.goalType(GoalType.FREE)
			.build();

		onboarding.update(InvestmentType.KOREA_FOCUSED, 200000, GoalType.RETIREMENT, 5000000,
			(short) 24, (short) 10);

		assertThat(onboarding.getInvestmentType()).isEqualTo(InvestmentType.KOREA_FOCUSED);
		assertThat(onboarding.getMonthlyAmount()).isEqualTo(200000);
		assertThat(onboarding.getGoalType()).isEqualTo(GoalType.RETIREMENT);
		assertThat(onboarding.getGoalAmount()).isEqualTo(5000000);
		assertThat(onboarding.getGoalPeriodMonths()).isEqualTo((short) 24);
		assertThat(onboarding.getSalaryDay()).isEqualTo((short) 10);
	}
}
