import axios from 'axios';
import * as SecureStore from 'expo-secure-store';
import { API_BASE_URL, ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY } from '../utils/constants';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터 — Access Token 자동 첨부
apiClient.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 응답 인터셉터 — 401 시 토큰 재발급
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
        const response = await axios.post(`${API_BASE_URL}/api/auth/reissue`, { refreshToken });
        const newAccessToken = response.data.data.accessToken;

        await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, newAccessToken);
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
      } catch {
        // Refresh 실패 → 로그아웃 처리
        await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
        await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
        // TODO: 로그인 화면으로 이동
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
