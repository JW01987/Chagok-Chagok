package com.chagok.presentation.user.dto;

import com.chagok.domain.user.GoalType;
import com.chagok.domain.user.InvestmentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {

	@NotNull(message = "투자 성향을 선택해주세요")
	private InvestmentType investmentType;

	@NotNull(message = "월 투자 금액을 입력해주세요")
	@Min(value = 50000, message = "월 투자 금액은 50,000원 이상이어야 합니다")
	@Max(value = 1000000, message = "월 투자 금액은 1,000,000원 이하여야 합니다")
	private Integer monthlyAmount;

	@NotNull(message = "투자 목표를 선택해주세요")
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
