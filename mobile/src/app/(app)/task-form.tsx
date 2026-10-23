import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { ApiError, isApiError } from '@/api/errors';
import { createTask } from '@/api/tasks';
import { CATEGORIES, Category } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { DeadlineField } from '@/components/DeadlineField';
import { Screen } from '@/components/Screen';
import { Sheet, SheetItem } from '@/components/Sheet';
import { useSnackbar } from '@/components/Snackbar';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { clearAnalysis } from '@/tasks/analyzeMemory';
import { refreshTaskData } from '@/tasks/cache';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';
import { parseHours, TaskFormInput, TaskFormOutput, taskFormSchema } from '@/utils/validators';

type Params = { mode?: string; aiOff?: string; title?: string; category?: string; hours?: string; deadline?: string; description?: string };

// Server field names -> form field names, for errors that come back from POST /tasks
const SERVER_FIELDS: Record<string, keyof TaskFormInput> = {
  title: 'title', category: 'category', estimatedHours: 'hours', deadline: 'deadline', description: 'description',
};

// S5 Review (AI suggestion, prefilled) and S6 Manual (empty fields) are the same form (UI plan 7.7, 7.8).
// There is no priority field anywhere: the server computes it.
export default function TaskFormScreen() {
  const p = useLocalSearchParams<Params>();
  const review = p.mode === 'review';
  const queryClient = useQueryClient();
  const showSnack = useSnackbar();
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const { control, handleSubmit, setError, setValue, formState } = useForm<TaskFormInput, unknown, TaskFormOutput>({
    resolver: zodResolver(taskFormSchema),
    mode: 'onBlur',
    // Manual entry starts empty, even after the AI failed (SRS 8.5); the typed text and deadline carry over
    defaultValues: {
      title: review ? (p.title ?? '') : '',
      category: review ? (p.category ?? '') : '',
      hours: review ? (p.hours ?? '') : '',
      deadline: p.deadline ? new Date(p.deadline) : null,
      description: p.description ?? '',
    },
  });

  const save = handleSubmit(async (form) => {
    setFormError(null);
    try {
      await createTask({
        title: form.title.trim(),
        category: form.category as Category,
        estimatedHours: parseHours(form.hours)!,
        deadline: form.deadline!.toISOString(),
        description: form.description.trim() || undefined,
      });
      clearAnalysis();
      await refreshTaskData(queryClient);
      showSnack({ message: strings.tasks.saved });
      router.dismissAll();
    } catch (e) {
      if (isApiError(e, 'VALIDATION') && Object.keys(e.fieldErrors).length) {
        for (const [field, message] of Object.entries(e.fieldErrors)) {
          const name = SERVER_FIELDS[field];
          if (name) setError(name, { message });
        }
        return;
      }
      setFormError(e instanceof ApiError ? e.message : strings.errors.SERVER);
    }
  });

  return (
    <Screen edges={['bottom']}>
      <Stack.Screen options={{ title: review ? strings.create.reviewTitle : strings.create.manualTitle }} />
      {review ? <Banner tone="info" message={strings.create.reviewBanner} /> : null}
      {p.aiOff ? <Banner tone="warning" message={strings.create.aiOffBanner} /> : null}

      <Controller
        control={control}
        name="title"
        render={({ field, fieldState }) => (
          <TextField label={strings.create.taskTitle} maxLength={200} value={field.value} onChangeText={field.onChange} onBlur={field.onBlur} error={fieldState.error?.message} />
        )}
      />

      <Controller
        control={control}
        name="category"
        render={({ field, fieldState }) => (
          <View style={styles.field}>
            <AppText variant="label">{strings.create.category}</AppText>
            <Pressable onPress={() => setCategoryOpen(true)} accessibilityRole="button" accessibilityLabel={strings.create.category} style={[styles.select, fieldState.error && styles.selectError]}>
              <AppText style={field.value ? undefined : styles.placeholder}>
                {field.value ? strings.category[field.value as Category] : strings.create.chooseCategory}
              </AppText>
              <AppText style={styles.placeholder}>▾</AppText>
            </Pressable>
            {fieldState.error ? <AppText variant="caption" style={styles.error}>{fieldState.error.message}</AppText> : null}
            <Sheet visible={categoryOpen} onClose={() => setCategoryOpen(false)} title={strings.create.category}>
              <ScrollView>
                {CATEGORIES.map((c) => (
                  <SheetItem
                    key={c}
                    label={strings.category[c]}
                    selected={field.value === c}
                    onPress={() => {
                      setValue('category', c, { shouldValidate: true });
                      setCategoryOpen(false);
                    }}
                  />
                ))}
              </ScrollView>
            </Sheet>
          </View>
        )}
      />

      <Controller
        control={control}
        name="hours"
        render={({ field, fieldState }) => {
          // Stepper moves 0.5; typing accepts 0.1 steps (UI plan 7.7)
          const step = (delta: number) => {
            const current = parseHours(field.value) ?? 0;
            const next = Math.min(999.5, Math.max(0.5, Math.round((current + delta) * 10) / 10));
            setValue('hours', String(next), { shouldValidate: true });
          };
          return (
            <View style={styles.field}>
              <AppText variant="label">{strings.create.hours}</AppText>
              <View style={styles.stepperRow}>
                <StepButton label="−" accessibilityLabel={strings.create.decrease} onPress={() => step(-0.5)} />
                <TextInput
                  value={field.value}
                  onChangeText={field.onChange}
                  onBlur={field.onBlur}
                  keyboardType="decimal-pad"
                  placeholder={strings.create.hoursPlaceholder}
                  placeholderTextColor={colors.muted}
                  accessibilityLabel={strings.create.hours}
                  style={[styles.hoursInput, fieldState.error && styles.selectError]}
                />
                <StepButton label="+" accessibilityLabel={strings.create.increase} onPress={() => step(0.5)} />
              </View>
              {fieldState.error ? <AppText variant="caption" style={styles.error}>{fieldState.error.message}</AppText> : null}
            </View>
          );
        }}
      />

      <Controller
        control={control}
        name="deadline"
        render={({ field, fieldState }) => (
          <DeadlineField label={strings.create.deadlineLabel} value={field.value} onChange={(d) => setValue('deadline', d, { shouldValidate: true })} error={fieldState.error?.message} />
        )}
      />

      <Controller
        control={control}
        name="description"
        render={({ field, fieldState }) => (
          <TextField
            label={strings.create.description}
            placeholder={strings.create.descriptionPlaceholder}
            multiline
            maxLength={2000}
            textAlignVertical="top"
            style={styles.description}
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={fieldState.error?.message}
          />
        )}
      />

      <AppText variant="caption">{strings.create.priorityHint}</AppText>
      <Button label={strings.create.save} onPress={save} loading={formState.isSubmitting} />
      {formError ? <AppText style={[styles.error, styles.center]}>{formError}</AppText> : null}
    </Screen>
  );
}

function StepButton({ label, accessibilityLabel, onPress }: { label: string; accessibilityLabel: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={accessibilityLabel} style={styles.step}>
      <AppText style={styles.stepText}>{label}</AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  field: { gap: space.xs },
  select: {
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
  selectError: { borderColor: colors.dueTomorrow },
  placeholder: { color: colors.muted },
  error: { color: colors.dueTomorrow },
  center: { textAlign: 'center' },
  stepperRow: { flexDirection: 'row', alignItems: 'center', gap: space.s },
  step: {
    width: minTouch + 4,
    height: minTouch + 4,
    borderRadius: radius.input,
    borderWidth: 1,
    borderColor: colors.brand,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepText: { fontFamily: fonts.bodyBold, fontSize: 20, color: colors.brand },
  hoursInput: {
    flex: 1,
    minHeight: minTouch + 4,
    borderWidth: 1,
    borderColor: colors.inputBorder,
    borderRadius: radius.input,
    backgroundColor: colors.surface,
    paddingHorizontal: space.m,
    textAlign: 'center',
    fontFamily: fonts.body,
    fontSize: 15,
    color: colors.ink,
  },
  description: { minHeight: 96 },
});
