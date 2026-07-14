import { StyleSheet, Text, View } from 'react-native';

interface LineChartProps {
  data: unknown[];
}

// TODO: victory-native 기반 실제 라인 차트 구현 (데이터 포맷은 시뮬레이션 태스크에서 확정)
export default function LineChart({ data }: LineChartProps) {
  return (
    <View style={styles.placeholder}>
      <Text>LineChart ({data.length})</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  placeholder: {
    alignItems: 'center',
    justifyContent: 'center',
  },
});
