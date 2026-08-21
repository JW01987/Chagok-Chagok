package com.chagok.domain.portfolio;

import com.chagok.domain.user.InvestmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioTemplateRepository extends JpaRepository<PortfolioTemplate, Long> {

	List<PortfolioTemplate> findByInvestmentTypeAndActiveTrueOrderByDisplayOrderAsc(InvestmentType investmentType);
}
