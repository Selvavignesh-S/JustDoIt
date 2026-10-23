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
  onComplete?: () => void; // pending cards only
  onMenu: () => void;
  onPress?: () => void;
};

// UI plan 6, 7.5, 7.12. Pending: check circle, title, deadline line, chips, ⋮, stripe in the priority color.
// Completed: muted, green filled check (not tappable), green stripe, muted priority chip, "Was due …".
export function TaskCard({ task, completing, onComplete, onMenu, onPress }: Props) {
  const done = task.status === 'COMPLETED';
  const stripe = done ? colors.done : priorityColors[task.currentPriority].fg;
  const deadline = formatDateTime(task.deadline);
  const deadlineLine = done
    ? strings.completedList.wasDue(deadline)
    : // Overdue cards show the absolute time only; the Overdue chip itself comes from the server flag
      task.overdue
      ? deadline
      : `${deadline} · ${relativeFromNow(task.deadline)}`;
  return (
    // The body is the open-detail target, a sibling of the check and ⋮ (never nest pressables: nested <button> on web, hidden controls for VoiceOver)
    <View style={[styles.card, { borderLeftColor: stripe }, done && styles.muted]}>
      {done ? (
        <View style={styles.checkHit} accessibilityLabel={strings.completedList.completedLabel(task.title)}>
          <View style={[styles.check, styles.checkDone]}>
            <Feather name="check" size={14} color={colors.white} />
          </View>
        </View>
      ) : (
        // 22-pt circle, 44-pt touch area
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
      )}

      <Pressable onPress={onPress} disabled={!onPress} accessibilityRole={onPress ? 'button' : undefined} style={styles.body}>
        {/* Only titles truncate, at 2 lines (UI plan 3.2) */}
        <AppText variant="bodyStrong" numberOfLines={2}>
          {task.title}
        </AppText>
        <AppText variant="caption">{deadlineLine}</AppText>
        <View style={styles.chips}>
          <Chip label={strings.category[task.category]} />
          {/* A completed task's priority is frozen at the moment it was completed (SRS 11) */}
          <PriorityChip priority={task.currentPriority} muted={done} />
          {task.overdue ? <OverdueChip /> : null}
          <Chip label={formatHours(task.estimatedHours)} />
        </View>
      </Pressable>

      <Pressable onPress={onMenu} hitSlop={8} accessibilityRole="button" accessibilityLabel={strings.tasks.moreActions(task.title)} style={styles.menu}>
        <Feather name="more-vertical" size={20} color={colors.muted} />
      </Pressable>
    </View>
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
  check: { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: colors.checkBorder, alignItems: 'center', justifyContent: 'center' },
  checkDone: { backgroundColor: colors.done, borderColor: colors.done },
  muted: { opacity: 0.78 },
  body: { flex: 1, gap: space.xs },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space.xs, marginTop: 2 },
  menu: { width: minTouch, height: minTouch - 12, alignItems: 'center', justifyContent: 'center' },
});
