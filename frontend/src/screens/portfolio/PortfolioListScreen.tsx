import { StyleSheet, Text, View } from 'react-native';

export default function PortfolioListScreen() {
  return (
    <View style={styles.container}>
      <Text>PortfolioListScreen</Text>
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
