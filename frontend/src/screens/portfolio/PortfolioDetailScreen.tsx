import { StyleSheet, Text, View } from 'react-native';

export default function PortfolioDetailScreen() {
  return (
    <View style={styles.container}>
      <Text>PortfolioDetailScreen</Text>
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
