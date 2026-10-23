import { Feather } from '@expo/vector-icons';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';

import { Task } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Chip, OverdueChip, PriorityChip, priorityColors } from '@/components/Chips';
import { strings } from '@/strings/en';
import { colors, minTouch, radius, space } from '@/theme/tokens';
import { formatDateTime, formatHours, relativeFromNow } from '@/utils/format';

type Props = {
  task: Task;
  completing?: boolean;
  onComplete: () => void;
  onMenu: () => void;
  onPress?: () => void;
};

// A pending task (UI plan 6, 7.5): check circle, title, deadline line, chips, ⋮ menu. 4-pt stripe in the priority color.
export function TaskCard({ task, completing, onComplete, onMenu, onPress }: Props) {
  const stripe = priorityColors[task.currentPriority].fg;
  const deadline = formatDateTime(task.deadline);
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? 'button' : undefined}
      style={[styles.card, { borderLeftColor: stripe }]}>
      {/* 22-pt circle, 44-pt touch area */}
      <Pressable
        onPress={onComplete}
        disabled={completing}
        hitSlop={11}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: false, busy: completing }}
        accessibilityLabel={strings.tasks.markComplete(task.title)}
        style={styles.checkHit}>
        {completing ? <ActivityIndicator size="small" color={colors.brand} /> : <View style={styles.check} />}
      </Pressable>

      <View style={styles.body}>
        {/* Only titles truncate, at 2 lines (UI plan 3.2) */}
        <AppText variant="bodyStrong" numberOfLines={2}>
          {task.title}
        </AppText>
        <AppText variant="caption">
          {/* Overdue cards show the absolute time only; the Overdue chip itself comes from the server flag */}
          {task.overdue ? deadline : `${deadline} · ${relativeFromNow(task.deadline)}`}
        </AppText>
        <View style={styles.chips}>
          <Chip label={strings.category[task.category]} />
          <PriorityChip priority={task.currentPriority} />
          {task.overdue ? <OverdueChip /> : null}
          <Chip label={formatHours(task.estimatedHours)} />
        </View>
      </View>

      <Pressable onPress={onMenu} hitSlop={8} accessibilityRole="button" accessibilityLabel={strings.tasks.moreActions(task.title)} style={styles.menu}>
        <Feather name="more-vertical" size={20} color={colors.muted} />
      </Pressable>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.s,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.line,
    borderLeftWidth: 4,
    borderRadius: radius.card,
    paddingVertical: space.m,
    paddingRight: space.xs,
    paddingLeft: space.s,
  },
  checkHit: { width: minTouch - 12, height: minTouch - 12, alignItems: 'center', justifyContent: 'center' },
  check: { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: colors.checkBorder },
  body: { flex: 1, gap: space.xs },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space.xs, marginTop: 2 },
  menu: { width: minTouch, height: minTouch - 12, alignItems: 'center', justifyContent: 'center' },
});
