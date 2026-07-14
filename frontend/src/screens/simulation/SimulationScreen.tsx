import { StyleSheet, Text, View } from 'react-native';

export default function SimulationScreen() {
  return (
    <View style={styles.container}>
      <Text>SimulationScreen</Text>
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
