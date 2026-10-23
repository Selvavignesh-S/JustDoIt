import { StyleSheet, View } from 'react-native';

import { Priority } from '@/api/types';
import { AppText } from '@/components/AppText';
import { strings } from '@/strings/en';
import { colors, radius, space, type } from '@/theme/tokens';

// The four priority colors are the only loud colors on screen; each always comes with a word (UI plan 3.1, 3.4)
export const priorityColors: Record<Priority, { fg: string; bg: string }> = {
  DUE_TOMORROW: { fg: colors.dueTomorrow, bg: colors.dueTomorrowTint },
  HIGH: { fg: colors.high, bg: colors.highTint },
  MEDIUM: { fg: colors.medium, bg: colors.mediumTint },
  LOW: { fg: colors.low, bg: colors.lowTint },
};

export function PriorityChip({ priority, muted }: { priority: Priority; muted?: boolean }) {
  const c = priorityColors[priority];
  return (
    <View style={[styles.chip, { backgroundColor: c.bg }, muted && styles.muted]}>
      <View style={[styles.dot, { backgroundColor: c.fg }]} />
      <AppText style={[type.chip, { color: c.fg }]}>{strings.priority[priority]}</AppText>
    </View>
  );
}

// Overdue is not a priority: an outlined chip, shown only when the server flags the task
export function OverdueChip() {
  return (
    <View style={[styles.chip, styles.overdue]}>
      <AppText style={[type.chip, { color: colors.dueTomorrow }]}>{strings.tasks.overdue}</AppText>
    </View>
  );
}

// Category and hours: neutral grey so they never compete with urgency
export function Chip({ label }: { label: string }) {
  return (
    <View style={[styles.chip, { backgroundColor: colors.neutralChipBg }]}>
      <AppText style={[type.chip, { color: colors.neutralChipText }]}>{label}</AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    borderRadius: radius.pill,
    paddingHorizontal: space.s,
    paddingVertical: 3,
    alignSelf: 'flex-start',
  },
  dot: { width: 6, height: 6, borderRadius: 3 },
  muted: { opacity: 0.75 },
  overdue: { borderWidth: 1, borderColor: colors.dueTomorrow, backgroundColor: 'transparent' },
});
