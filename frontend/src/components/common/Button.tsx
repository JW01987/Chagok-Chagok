import { Pressable, PressableProps, StyleSheet, Text } from 'react-native';

interface ButtonProps extends PressableProps {
  label: string;
}

export default function Button({ label, style, ...rest }: ButtonProps) {
  return (
    <Pressable style={style} {...rest}>
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  label: {
    fontSize: 16,
  },
});
