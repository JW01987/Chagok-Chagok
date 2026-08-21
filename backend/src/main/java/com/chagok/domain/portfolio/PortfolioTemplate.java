package com.chagok.domain.portfolio;

import com.chagok.domain.user.InvestmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포트폴리오 마스터 데이터 — BT-11에서 전체 도메인이 구현될 예정이며,
 * 여기서는 BT-10 온보딩 완료 시 추천 포트폴리오를 조회하기 위한 읽기 전용 매핑만 둔다.
 */
@Entity
@Table(name = "portfolio_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "investment_type", nullable = false, length = 20)
	private InvestmentType investmentType;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "display_order", nullable = false)
	private short displayOrder;
}
