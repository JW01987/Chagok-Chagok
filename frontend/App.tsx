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
