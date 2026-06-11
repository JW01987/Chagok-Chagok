# BT-03 | 프론트엔드 프로젝트 초기화

- **최초 작성일**: 2026-06-10
- **업데이트**: 2026-06-10
- **Phase**: 0 — 환경 세팅
- **상태**: ⬜ 대기
- **선행 태스크**: BT-01
- **완료 기준**: Expo 앱 실행 + Hello World 화면 확인 + 폴더 구조 완성

---

## 개요

React Native (Expo) TypeScript 프로젝트를 초기화하고,
앱 전반에 사용될 폴더 구조, 의존성, 환경변수 설정을 완성한다.

---

## BT-03-01 | Expo 프로젝트 생성

**작업 유형**: 명령어 실행 (Claude Code 실행 가능)

### 실행 명령어

```bash
cd chagok-chagok/frontend

# Expo TypeScript 템플릿으로 생성
npx create-expo-app@latest . --template blank-typescript

# 생성 확인
ls -la
```

### 생성 확인

```bash
npx expo start
# QR 코드 출력 또는 Metro Bundler 실행 확인
```

### 완료 확인
- [ ] `frontend/app.json` 존재
- [ ] `npx expo start` 실행됨

---

## BT-03-02 | 핵심 의존성 설치

**작업 유형**: 명령어 실행 (Claude Code 실행 가능)

### 설치 명령어

```bash
cd chagok-chagok/frontend

# 네비게이션
npx expo install @react-navigation/native @react-navigation/native-stack @react-navigation/bottom-tabs
npx expo install react-native-screens react-native-safe-area-context

# 상태 관리
npm install zustand

# 서버 상태 / API 캐싱
npm install @tanstack/react-query axios

# 보안 저장소 (토큰 저장)
npx expo install expo-secure-store

# 로컬 저장소 (게스트 온보딩)
npx expo install @react-native-async-storage/async-storage

# 차트
npm install victory-native

# 푸시 알림
npx expo install expo-notifications expo-device

# 기기 정보 (루팅/탈옥 감지)
npm install react-native-device-info

# 유틸
npm install dayjs
```

### package.json scripts 추가

```json
{
  "scripts": {
    "start": "expo start",
    "android": "expo start --android",
    "ios": "expo start --ios",
    "web": "expo start --web",
    "typecheck": "tsc --noEmit",
    "lint": "eslint . --ext .ts,.tsx"
  }
}
```

### 완료 확인
- [ ] `npm install` 오류 없음
- [ ] `npx expo start` 재실행 가능

---

## BT-03-03 | 폴더 구조 생성

**작업 유형**: 디렉토리/파일 생성 (Claude Code 실행 가능)

### 생성할 구조

```
frontend/
├── src/
│   ├── screens/              ← 화면 컴포넌트
│   │   ├── auth/
│   │   │   ├── LoginScreen.tsx
│   │   │   └── SignupScreen.tsx
│   │   ├── onboarding/
│   │   │   └── OnboardingScreen.tsx
│   │   ├── dashboard/
│   │   │   └── DashboardScreen.tsx
│   │   ├── portfolio/
│   │   │   ├── PortfolioListScreen.tsx
│   │   │   └── PortfolioDetailScreen.tsx
│   │   ├── simulation/
│   │   │   └── SimulationScreen.tsx
│   │   └── settings/
│   │       └── SettingsScreen.tsx
│   │
│   ├── components/           ← 재사용 컴포넌트
│   │   ├── common/
│   │   │   ├── Button.tsx
│   │   │   ├── Input.tsx
│   │   │   ├── Card.tsx
│   │   │   └── LoadingSpinner.tsx
│   │   ├── charts/
│   │   │   ├── DonutChart.tsx
│   │   │   └── LineChart.tsx
│   │   └── portfolio/
│   │
│   ├── navigation/           ← 네비게이션 설정
│   │   ├── RootNavigator.tsx
│   │   ├── AuthNavigator.tsx
│   │   └── MainTabNavigator.tsx
│   │
│   ├── store/                ← Zustand 전역 상태
│   │   ├── authStore.ts
│   │   ├── onboardingStore.ts
│   │   └── portfolioStore.ts
│   │
│   ├── api/                  ← API 호출 함수
│   │   ├── client.ts         ← axios 인스턴스
│   │   ├── auth.ts
│   │   ├── portfolio.ts
│   │   ├── simulation.ts
│   │   └── savings.ts
│   │
│   ├── hooks/                ← 커스텀 훅
│   │   ├── useAuth.ts
│   │   └── usePortfolio.ts
│   │
│   ├── utils/                ← 유틸 함수
│   │   ├── format.ts         ← 금액/날짜 포맷
│   │   ├── storage.ts        ← AsyncStorage 래퍼
│   │   └── constants.ts
│   │
│   └── types/                ← TypeScript 타입 정의
│       ├── auth.ts
│       ├── portfolio.ts
│       ├── savings.ts
│       └── api.ts
│
├── app.config.ts             ← Expo 환경 설정
├── App.tsx                   ← 진입점
└── tsconfig.json
```

### 각 디렉토리에 index.ts 배럴 파일 생성

```bash
# 예시 — 각 디렉토리에 빈 index.ts 생성
touch src/screens/auth/index.ts
touch src/components/common/index.ts
# ... (나머지 동일)
```

### 완료 확인
- [ ] `src/` 하위 디렉토리 모두 생성

---

## BT-03-04 | 환경변수 설정

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 수정 파일: `frontend/app.config.ts`

```typescript
import { ExpoConfig, ConfigContext } from 'expo/config';

const ENV = {
  local: {
    API_BASE_URL: 'http://localhost:8080',
    ENV_NAME: 'local',
  },
  dev: {
    API_BASE_URL: 'https://api-dev.chagok.com',
    ENV_NAME: 'development',
  },
  prod: {
    API_BASE_URL: 'https://api.chagok.com',
    ENV_NAME: 'production',
  },
};

const getEnv = () => {
  const env = process.env.APP_ENV as keyof typeof ENV;
  return ENV[env] ?? ENV.local;
};

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: '차곡차곡',
  slug: 'chagok-chagok',
  version: '1.0.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'light',
  splash: {
    image: './assets/splash.png',
    resizeMode: 'contain',
    backgroundColor: '#4F7FFF',
  },
  ios: {
    supportsTablet: false,
    bundleIdentifier: 'com.chagok.app',
  },
  android: {
    adaptiveIcon: {
      foregroundImage: './assets/adaptive-icon.png',
      backgroundColor: '#4F7FFF',
    },
    package: 'com.chagok.app',
    googleServicesFile: './google-services.json',
  },
  extra: {
    ...getEnv(),
    eas: {
      projectId: '',  // EAS 등록 후 채우기
    },
  },
  plugins: [
    'expo-secure-store',
    'expo-notifications',
  ],
});
```

### 생성 파일: `frontend/src/utils/constants.ts`

```typescript
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
```

### 완료 확인
- [ ] `APP_ENV=local npx expo start` 로 로컬 API URL 사용 확인

---

## BT-03-05 | axios 클라이언트 & 기본 화면 구성

**작업 유형**: 파일 생성 (Claude Code 실행 가능)

### 생성 파일: `frontend/src/api/client.ts`

```typescript
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
```

### 수정 파일: `frontend/App.tsx` (확인용 Hello World)

```typescript
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient();

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <View style={styles.container}>
          <Text style={styles.title}>차곡차곡 🌱</Text>
          <Text style={styles.subtitle}>첫 월급부터 차곡차곡 모으는 투자 습관</Text>
        </View>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F8FAFF',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#4F7FFF',
  },
  subtitle: {
    marginTop: 8,
    fontSize: 14,
    color: '#888',
  },
});
```

### 완료 확인

```bash
npx expo start
# 시뮬레이터 또는 Expo Go 앱에서 "차곡차곡 🌱" 화면 확인
```

---

## 완료 체크리스트

- [ ] BT-03-01: `npx expo start` 실행됨
- [ ] BT-03-02: 핵심 의존성 설치 완료 (네비게이션, Zustand, React Query 등)
- [ ] BT-03-03: `src/` 폴더 구조 완성
- [ ] BT-03-04: `app.config.ts` 환경별 API URL 분리 완료
- [ ] BT-03-05: 앱 실행 시 Hello World 화면 출력

**다음 태스크**: BT-05 (CI 파이프라인)

---

_최초 작성: 2026-06-10 | 업데이트: 2026-06-10_
