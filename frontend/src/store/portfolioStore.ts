import { create } from 'zustand';

interface PortfolioState {
  selectedPortfolioId: string | null;
  selectPortfolio: (id: string | null) => void;
}

export const usePortfolioStore = create<PortfolioState>((set) => ({
  selectedPortfolioId: null,
  selectPortfolio: (id) => set({ selectedPortfolioId: id }),
}));
