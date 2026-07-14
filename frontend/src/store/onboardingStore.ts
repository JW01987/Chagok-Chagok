import { create } from 'zustand';

interface OnboardingState {
  isCompleted: boolean;
  completeOnboarding: () => void;
}

export const useOnboardingStore = create<OnboardingState>((set) => ({
  isCompleted: false,
  completeOnboarding: () => set({ isCompleted: true }),
}));
