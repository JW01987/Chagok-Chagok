package com.chagok.presentation.user.dto;

import com.chagok.domain.user.GoalType;
import com.chagok.domain.user.InvestmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PATCH 전용 — null인 필드는 변경하지 않는다. 그래서 필수값 검증(@NotNull)은 두지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingUpdateRequest {

	private InvestmentType investmentType;

	@Min(value = 50000, message = "월 투자 금액은 50,000원 이상이어야 합니다")
	@Max(value = 1000000, message = "월 투자 금액은 1,000,000원 이하여야 합니다")
	private Integer monthlyAmount;

	private GoalType goalType;

	@Min(value = 0, message = "목표 금액은 0 이상이어야 합니다")
	private Integer goalAmount;

	@Min(value = 1, message = "목표 기간은 1개월 이상이어야 합니다")
	@Max(value = 360, message = "목표 기간은 360개월 이하여야 합니다")
	private Integer goalPeriodMonths;

	@Min(value = 1, message = "급여일은 1일 이상이어야 합니다")
	@Max(value = 28, message = "급여일은 28일 이하여야 합니다")
	private Integer salaryDay;
}
