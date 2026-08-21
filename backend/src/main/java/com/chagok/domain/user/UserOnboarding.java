package com.chagok.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_onboarding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOnboarding {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "investment_type", nullable = false, length = 20)
	private InvestmentType investmentType;

	@Column(name = "monthly_amount", nullable = false)
	private Integer monthlyAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "goal_type", nullable = false, length = 20)
	private GoalType goalType;

	@Column(name = "goal_amount")
	private Integer goalAmount;

	// DB 컬럼이 SMALLINT라 Integer가 아닌 Short로 매핑한다 (goal_period_months, salary_day 모두 동일).
	@Column(name = "goal_period_months")
	private Short goalPeriodMonths;

	@Column(name = "salary_day")
	private Short salaryDay;

	@Column(name = "onboarding_completed_at")
	private LocalDateTime onboardingCompletedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public UserOnboarding(User user, InvestmentType investmentType, Integer monthlyAmount, GoalType goalType,
			Integer goalAmount, Short goalPeriodMonths, Short salaryDay) {
		this.user = user;
		this.investmentType = investmentType;
		this.monthlyAmount = monthlyAmount;
		this.goalType = goalType;
		this.goalAmount = goalAmount;
		this.goalPeriodMonths = goalPeriodMonths;
		this.salaryDay = salaryDay;
		this.onboardingCompletedAt = LocalDateTime.now();
	}

	public void update(InvestmentType investmentType, Integer monthlyAmount, GoalType goalType,
			Integer goalAmount, Short goalPeriodMonths, Short salaryDay) {
		if (investmentType != null) {
			this.investmentType = investmentType;
		}
		if (monthlyAmount != null) {
			this.monthlyAmount = monthlyAmount;
		}
		if (goalType != null) {
			this.goalType = goalType;
		}
		if (goalAmount != null) {
			this.goalAmount = goalAmount;
		}
		if (goalPeriodMonths != null) {
			this.goalPeriodMonths = goalPeriodMonths;
		}
		if (salaryDay != null) {
			this.salaryDay = salaryDay;
		}
	}
}
