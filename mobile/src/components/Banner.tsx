import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors, fonts, radius, space } from '@/theme/tokens';

type Tone = 'info' | 'warning' | 'error';

// Info / warning / error banner (UI plan section 6). Text always carries the meaning, never color alone.
export function Banner({ tone = 'info', title, message }: { tone?: Tone; title?: string; message: string }) {
  const palette = tones[tone];
  return (
    <View style={[styles.box, { backgroundColor: palette.bg }]} accessibilityRole="alert">
      {title ? <AppText style={[styles.title, { color: palette.fg }]}>{title}</AppText> : null}
      <AppText style={{ color: palette.fg }}>{message}</AppText>
    </View>
  );
}

const tones = {
  info: { bg: colors.brandTint, fg: colors.infoInk },
  warning: { bg: colors.warnBg, fg: colors.warnText },
  error: { bg: colors.dueTomorrowTint, fg: colors.dueTomorrow },
} as const;

const styles = StyleSheet.create({
  box: { borderRadius: radius.input, padding: space.m, gap: space.xs },
  title: { fontFamily: fonts.bodySemi },
});
