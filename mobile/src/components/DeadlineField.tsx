import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { createElement, useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { strings } from '@/strings/en';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';
import { endOfDay, formatDateTime } from '@/utils/format';

type Props = { label: string; value: Date | null; onChange: (date: Date) => void; error?: string; hint?: string };

// Date + time picker (UI plan 6). Past dates can't be picked; choosing only a date gives 11:59 PM (D20).
// The phone's clock is used only to block past times in the picker, never to decide overdue (UI plan 8.5).
export function DeadlineField({ label, value, onChange, error, hint }: Props) {
  const [iosOpen, setIosOpen] = useState(false);
  const now = new Date();

  const open = () => {
    if (Platform.OS === 'android') {
      // Android: the system date dialog, then the time dialog
      DateTimePickerAndroid.open({
        value: value ?? now,
        mode: 'date',
        minimumDate: now,
        onValueChange: (_e, date) => {
          const withDefaultTime = value ? withTimeOf(date, value) : endOfDay(date);
          onChange(withDefaultTime);
          DateTimePickerAndroid.open({
            value: withDefaultTime,
            mode: 'time',
            onValueChange: (_t, time) => onChange(time),
          });
        },
      });
    } else {
      setIosOpen((o) => !o);
    }
  };

  return (
    <View style={styles.wrap}>
      <AppText variant="label">{label}</AppText>
      {Platform.OS === 'web' ? (
        // Web preview only: the browser's own date-time input
        createElement('input', {
          type: 'datetime-local',
          'aria-label': label,
          min: toLocalInput(now),
          value: value ? toLocalInput(value) : '',
          onChange: (e: { target: { value: string } }) => e.target.value && onChange(new Date(e.target.value)),
          style: webInputStyle(!!error),
        })
      ) : (
        <Pressable onPress={open} accessibilityRole="button" accessibilityLabel={label} accessibilityHint={error ?? hint} style={[styles.box, error ? styles.boxError : null]}>
          <AppText style={value ? undefined : styles.placeholder}>{value ? formatDateTime(value) : strings.deadline.pick}</AppText>
          <AppText style={styles.chevron}>▾</AppText>
        </Pressable>
      )}
      {Platform.OS === 'ios' && iosOpen ? (
        <DateTimePicker value={value ?? endOfDay(now)} mode="datetime" display="inline" minimumDate={now} onValueChange={(_e, date) => onChange(date)} />
      ) : null}
      {error ? <AppText variant="caption" style={styles.error}>{error}</AppText> : hint ? <AppText variant="caption">{hint}</AppText> : null}
    </View>
  );
}

function withTimeOf(date: Date, time: Date) {
  const d = new Date(date);
  d.setHours(time.getHours(), time.getMinutes(), 0, 0);
  return d;
}

// "2026-10-24T23:59" in local time, the format <input type="datetime-local"> uses
function toLocalInput(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function webInputStyle(hasError: boolean) {
  return {
    minHeight: minTouch + 4,
    borderWidth: 1,
    borderStyle: 'solid',
    borderColor: hasError ? colors.dueTomorrow : colors.inputBorder,
    borderRadius: radius.input,
    padding: '0 12px',
    fontFamily: fonts.body,
    fontSize: 15,
    color: colors.ink,
    backgroundColor: colors.surface,
  };
}

const styles = StyleSheet.create({
  wrap: { gap: space.xs },
  box: {
    minHeight: minTouch + 4,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
    borderColor: colors.inputBorder,
    borderRadius: radius.input,
    backgroundColor: colors.surface,
    paddingHorizontal: space.m,
  },
  boxError: { borderColor: colors.dueTomorrow },
  placeholder: { color: colors.muted },
  chevron: { color: colors.muted },
  error: { color: colors.dueTomorrow },
});
