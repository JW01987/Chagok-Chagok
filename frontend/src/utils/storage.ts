import AsyncStorage from '@react-native-async-storage/async-storage';

export async function getStorageItem<T>(key: string): Promise<T | null> {
  const value = await AsyncStorage.getItem(key);
  return value ? (JSON.parse(value) as T) : null;
}

export async function setStorageItem<T>(key: string, value: T): Promise<void> {
  await AsyncStorage.setItem(key, JSON.stringify(value));
}

export async function removeStorageItem(key: string): Promise<void> {
  await AsyncStorage.removeItem(key);
}
