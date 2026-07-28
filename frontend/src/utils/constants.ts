import Constants from 'expo-constants';

export const API_BASE_URL = Constants.expoConfig?.extra?.API_BASE_URL as string;
export const ENV_NAME = Constants.expoConfig?.extra?.ENV_NAME as string;

// 투자 관련 상수
export const MIN_MONTHLY_AMOUNT = 50000;   // 5만원
export const MAX_MONTHLY_AMOUNT = 1000000; // 100만원
export const MIN_INVESTMENT_DAY = 1;
export const MAX_INVESTMENT_DAY = 28;

// 토큰 관련
export const ACCESS_TOKEN_KEY = 'access_token';
export const REFRESH_TOKEN_KEY = 'refresh_token';
