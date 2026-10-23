import { ReactNode } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { colors, minTouch, radius, space } from '@/theme/tokens';

type Props = { visible: boolean; onClose: () => void; title?: string; subtitle?: string; children: ReactNode };

// Bottom sheet built on React Native's Modal: no extra library needed for the menus and short forms in V1.
// Tapping the dimmed area or the Android back button closes it.
export function Sheet({ visible, onClose, title, subtitle, children }: Props) {
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.root}>
        <Pressable style={styles.scrim} onPress={onClose} accessibilityLabel="Close" />
        <View style={[styles.sheet, { paddingBottom: insets.bottom + space.l }]} accessibilityViewIsModal>
          <View style={styles.handle} />
          {title ? (
            <AppText variant="section" numberOfLines={2}>
              {title}
            </AppText>
          ) : null}
          {subtitle ? <AppText variant="caption">{subtitle}</AppText> : null}
          {children}
        </View>
      </View>
    </Modal>
  );
}

// One tappable row inside a sheet (menu item / option)
export function SheetItem({ label, onPress, destructive, selected }: { label: string; onPress: () => void; destructive?: boolean; selected?: boolean }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityState={{ selected }} style={({ pressed }) => [styles.item, pressed && styles.pressed]}>
      <AppText variant={selected ? 'bodyStrong' : 'body'} style={destructive ? { color: colors.dueTomorrow } : selected ? { color: colors.brand } : undefined}>
        {label}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, justifyContent: 'flex-end' },
  scrim: { ...StyleSheet.absoluteFill, backgroundColor: colors.scrim },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    paddingHorizontal: space.l,
    paddingTop: space.s,
    gap: space.s,
    maxHeight: '85%',
  },
  handle: { alignSelf: 'center', width: 36, height: 4, borderRadius: 2, backgroundColor: colors.line, marginBottom: space.s },
  item: { minHeight: minTouch + 4, justifyContent: 'center' },
  pressed: { opacity: 0.6 },
});
