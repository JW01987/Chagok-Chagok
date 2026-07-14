import { PropsWithChildren } from 'react';
import { StyleSheet, View, ViewProps } from 'react-native';

export default function Card({ children, style, ...rest }: PropsWithChildren<ViewProps>) {
  return (
    <View style={[styles.card, style]} {...rest}>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 12,
    padding: 16,
    backgroundColor: '#fff',
  },
});
