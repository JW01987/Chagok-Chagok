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
    // TODO: FCM 연동 태스크에서 google-services.json 추가 후 활성화
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
    [
      'expo-splash-screen',
      {
        image: './assets/splash.png',
        resizeMode: 'contain',
        backgroundColor: '#4F7FFF',
      },
    ],
  ],
});
