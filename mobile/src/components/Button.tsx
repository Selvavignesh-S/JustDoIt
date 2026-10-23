import { ActivityIndicator, Pressable, StyleSheet, ViewStyle } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';

type Variant = 'primary' | 'secondary' | 'destructive' | 'link';

type Props = {
  label: string;
  onPress: () => void;
  variant?: Variant;
  disabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
  accessibilityLabel?: string;
};

// UI plan 3.3: primary = filled brand, 48 high; secondary = brand outline; destructive = red text; disabled = 45% opacity
export function Button({ label, onPress, variant = 'primary', disabled, loading, style, accessibilityLabel }: Props) {
  const inactive = disabled || loading;
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      hitSlop={variant === 'link' ? 8 : 0}
      style={({ pressed }) => [
        styles.base,
        styles[variant],
        inactive && styles.inactive,
        pressed && !inactive && styles.pressed,
        style,
      ]}>
      {loading ? (
        <ActivityIndicator color={variant === 'primary' ? colors.white : colors.brand} />
      ) : (
        <AppText style={[styles.label, labelStyles[variant]]}>{label}</AppText>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: { minHeight: minTouch, borderRadius: radius.input, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.l },
  primary: { minHeight: 48, backgroundColor: colors.brand },
  secondary: { minHeight: 48, borderWidth: 1, borderColor: colors.brand, backgroundColor: 'transparent' },
  destructive: { minHeight: 48, backgroundColor: 'transparent' },
  link: { minHeight: minTouch, paddingHorizontal: space.xs, alignSelf: 'center' },
  inactive: { opacity: 0.45 },
  pressed: { opacity: 0.8 },
  label: { fontFamily: fonts.bodySemi, fontSize: 15 },
});

const labelStyles = StyleSheet.create({
  primary: { color: colors.white },
  secondary: { color: colors.brand },
  destructive: { color: colors.dueTomorrow },
  link: { color: colors.brand },
});
