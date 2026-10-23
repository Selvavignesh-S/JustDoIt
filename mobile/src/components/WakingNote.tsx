import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { subscribeSlow } from '@/api/client';
import { AppText } from '@/components/AppText';
import { strings } from '@/strings/en';
import { colors, radius, space } from '@/theme/tokens';

// Shown on every screen while any request has run past 4 s: the free Render server is waking up (UI plan 8.1)
export function WakingNote() {
  const [slow, setSlow] = useState(false);
  const insets = useSafeAreaInsets();
  useEffect(() => subscribeSlow(setSlow), []);
  if (!slow) return null;
  return (
    <View pointerEvents="none" style={[styles.wrap, { top: insets.top + space.s }]} accessibilityLiveRegion="polite">
      <AppText style={styles.text}>{strings.common.waking}</AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    position: 'absolute',
    left: space.l,
    right: space.l,
    backgroundColor: colors.warnBg,
    borderRadius: radius.input,
    padding: space.m,
    zIndex: 10,
  },
  text: { color: colors.warnText, textAlign: 'center' },
});
