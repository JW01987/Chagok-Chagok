import { StyleSheet, Text, View } from 'react-native';

export default function OnboardingScreen() {
  return (
    <View style={styles.container}>
      <Text>OnboardingScreen</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
