import { Pressable, StyleSheet, View } from 'react-native';

import { PRIORITIES, Priority } from '@/api/types';
import { AppText } from '@/components/AppText';
import { priorityColors } from '@/components/Chips';
import { strings } from '@/strings/en';
import { fonts, minTouch, radius, space } from '@/theme/tokens';

type Props = {
  counts?: Record<Priority, number>;
  selected: Priority | null;
  onSelect: (priority: Priority | null) => void;
};

// Four rows, most urgent first. Tapping filters the list to that priority; tapping again clears it (UI plan 6, 7.5).
// Counts come from /tasks/summary and never change with filters.
export function PriorityLadder({ counts, selected, onSelect }: Props) {
  return (
    <View style={styles.ladder}>
      {PRIORITIES.map((p) => {
        const c = priorityColors[p];
        const isSelected = selected === p;
        const count = counts?.[p];
        return (
          <Pressable
            key={p}
            onPress={() => onSelect(isSelected ? null : p)}
            accessibilityRole="button"
            accessibilityState={{ selected: isSelected }}
            accessibilityLabel={`${strings.priority[p]}, ${count ?? 0}`}
            style={[styles.row, { backgroundColor: c.bg, borderLeftColor: c.fg }, isSelected && { borderColor: c.fg, borderWidth: 2, borderLeftWidth: 4 }]}>
            <AppText style={[styles.label, { color: c.fg }]}>{strings.priority[p]}</AppText>
            <AppText style={[styles.count, { color: c.fg }]}>{count ?? '–'}</AppText>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  ladder: { gap: space.s },
  row: {
    minHeight: minTouch,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.m,
    borderRadius: radius.input,
    borderLeftWidth: 4,
    borderWidth: 0,
    borderColor: 'transparent',
  },
  label: { fontFamily: fonts.bodySemi, fontSize: 15 },
  count: { fontFamily: fonts.headingSemi, fontSize: 17 },
});
