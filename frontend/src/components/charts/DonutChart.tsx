import { StyleSheet, Text, View } from 'react-native';

interface DonutChartProps {
  data: unknown[];
}

// TODO: victory-native 기반 실제 도넛 차트 구현 (데이터 포맷은 포트폴리오 태스크에서 확정)
export default function DonutChart({ data }: DonutChartProps) {
  return (
    <View style={styles.placeholder}>
      <Text>DonutChart ({data.length})</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  placeholder: {
    alignItems: 'center',
    justifyContent: 'center',
  },
});
