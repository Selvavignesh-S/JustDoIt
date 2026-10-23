import { createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';

type Snack = { message: string; actionLabel?: string; onAction?: () => void };

const SnackbarContext = createContext<(snack: Snack) => void>(() => {});

// One message at a time, 5 seconds, optional action such as "Undo" (UI plan 6)
export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [snack, setSnack] = useState<Snack | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const insets = useSafeAreaInsets();

  const show = useCallback((next: Snack) => {
    if (timer.current) clearTimeout(timer.current);
    setSnack(next);
    timer.current = setTimeout(() => setSnack(null), 5_000);
  }, []);

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  return (
    <SnackbarContext.Provider value={show}>
      {children}
      {snack ? (
        <View style={[styles.wrap, { bottom: insets.bottom + 64 }]} accessibilityLiveRegion="polite" accessibilityRole="alert">
          <AppText style={styles.message}>{snack.message}</AppText>
          {snack.actionLabel && snack.onAction ? (
            <Pressable
              onPress={() => {
                setSnack(null);
                snack.onAction?.();
              }}
              accessibilityRole="button"
              hitSlop={8}
              style={styles.action}>
              <AppText style={styles.actionText}>{snack.actionLabel}</AppText>
            </Pressable>
          ) : null}
        </View>
      ) : null}
    </SnackbarContext.Provider>
  );
}

export function useSnackbar() {
  return useContext(SnackbarContext);
}

const styles = StyleSheet.create({
  wrap: {
    position: 'absolute',
    left: space.l,
    right: space.l,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.m,
    backgroundColor: colors.ink,
    borderRadius: radius.input,
    paddingLeft: space.l,
    paddingRight: space.s,
    minHeight: minTouch + 4,
  },
  message: { flex: 1, color: colors.white },
  action: { minHeight: minTouch, justifyContent: 'center', paddingHorizontal: space.m },
  actionText: { color: '#AEB8FF', fontFamily: fonts.bodyBold },
});
