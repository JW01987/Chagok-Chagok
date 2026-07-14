import { usePortfolioStore } from '../store/portfolioStore';

export function usePortfolio() {
  return usePortfolioStore();
}
