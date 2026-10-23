import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { ReactNode, useState } from 'react';
import { Controller, useForm, UseFormSetError } from 'react-hook-form';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { ApiError, isApiError } from '@/api/errors';
import { createTask, getTask, updateTask } from '@/api/tasks';
import { CATEGORIES, Category, Task } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DeadlineField } from '@/components/DeadlineField';
import { Screen } from '@/components/Screen';
import { Sheet, SheetItem } from '@/components/Sheet';
import { useSnackbar } from '@/components/Snackbar';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { clearAnalysis } from '@/tasks/analyzeMemory';
import { refreshTaskData } from '@/tasks/cache';
import { buildTaskPatch } from '@/tasks/taskPatch';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';
import { parseHours, TaskFormInput, TaskFormOutput, taskFormSchemaFor } from '@/utils/validators';

type Params = { mode?: string; id?: string; aiOff?: string; title?: string; category?: string; hours?: string; deadline?: string; description?: string };

// Server field names -> form field names, for errors that come back from POST/PATCH /tasks
const SERVER_FIELDS: Record<string, keyof TaskFormInput> = {
  title: 'title', category: 'category', estimatedHours: 'hours', deadline: 'deadline', description: 'description',
};

// One form, three modes (UI plan 6 "TaskForm"): S5 review (AI prefill), S6 manual (empty), and edit (UI plan 7.10).
// There is no priority field anywhere: the server computes it.
export default function TaskFormScreen() {
  const p = useLocalSearchParams<Params>();
  return p.mode === 'edit' ? <EditTask id={Number(p.id)} /> : <CreateTask p={p} />;
}

function CreateTask({ p }: { p: Params }) {
  const review = p.mode === 'review';
  const queryClient = useQueryClient();
  const showSnack = useSnackbar();

  // Manual entry starts empty, even after the AI failed (SRS 8.5); the typed text and deadline carry over
  const defaults: TaskFormInput = {
    title: review ? (p.title ?? '') : '',
    category: review ? (p.category ?? '') : '',
    hours: review ? (p.hours ?? '') : '',
    deadline: p.deadline ? new Date(p.deadline) : null,
    description: p.description ?? '',
  };

  const submit = async (form: TaskFormOutput) => {
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
  };

  return (
    <TaskFormBody
      title={review ? strings.create.reviewTitle : strings.create.manualTitle}
      defaults={defaults}
      submitLabel={strings.create.save}
      submit={submit}
      top={
        <>
          {review ? <Banner tone="info" message={strings.create.reviewBanner} /> : null}
          {p.aiOff ? <Banner tone="warning" message={strings.create.aiOffBanner} /> : null}
        </>
      }
      hint={strings.create.priorityHint}
    />
  );
}

function EditTask({ id }: { id: number }) {
  const queryClient = useQueryClient();
  const [conflict, setConflict] = useState(false);
  // Usually already cached from the detail screen
  const query = useQuery({ queryKey: ['task', id], queryFn: () => getTask(id) });
  const task = query.data;

  if (!task) {
    return query.isError ? (
      <Screen edges={['bottom']}>
        <Banner tone="warning" message={strings.detail.notAvailable} />
        <Button variant="secondary" label={strings.detail.goBack} onPress={() => router.back()} />
      </Screen>
    ) : (
      <ActivityIndicator style={styles.loading} color={colors.brand} />
    );
  }

  const original = {
    title: task.title,
    category: task.category,
    hours: task.estimatedHours,
    deadline: new Date(task.deadline),
    description: task.description ?? '',
  };

  const submit = async (form: TaskFormOutput) => {
    const changes = buildTaskPatch(original, {
      title: form.title,
      category: form.category,
      hours: parseHours(form.hours)!,
      deadline: form.deadline!,
      description: form.description,
    });
    try {
      const updated = await updateTask(task.id, changes, task.version);
      // The response carries the new row version and, if the deadline changed, the recalculated priority
      queryClient.setQueryData<Task>(['task', id], (old) => ({ ...updated, attachments: old?.attachments }));
      await refreshTaskData(queryClient);
      router.back();
    } catch (e) {
      // Changed on another device, or completed meanwhile: the edits are discarded (UI plan 7.10)
      if (isApiError(e, 'CONFLICT') || isApiError(e, 'TASK_COMPLETED')) {
        setConflict(true);
        return;
      }
      throw e;
    }
  };

  return (
    <>
      <TaskFormBody
        title={strings.edit.title}
        defaults={{ ...original, hours: String(original.hours) }}
        originalDeadline={original.deadline.getTime()}
        submitLabel={strings.edit.save}
        submit={submit}
        onlyWhenChanged
        top={
          // initialPriority is locked forever (SRS 11)
          <View style={styles.locked}>
            <AppText variant="caption">{strings.edit.initialPriority}</AppText>
            <AppText variant="bodyStrong">{strings.priority[task.initialPriority]}</AppText>
            <AppText variant="caption">{strings.edit.initialNote}</AppText>
          </View>
        }
        hint={strings.edit.currentHint}
      />
      <ConfirmDialog
        visible={conflict}
        title={strings.tasks.conflictTitle}
        body={strings.edit.conflictNote}
        confirmLabel={strings.tasks.reload}
        onConfirm={() => {
          setConflict(false);
          void query.refetch();
          void refreshTaskData(queryClient);
          router.back();
        }}
      />
    </>
  );
}

type BodyProps = {
  title: string;
  defaults: TaskFormInput;
  originalDeadline?: number;
  submitLabel: string;
  submit: (form: TaskFormOutput) => Promise<void>;
  top?: ReactNode;
  hint: string;
  // Edit: Save stays disabled until something changed (UI plan 7.10)
  onlyWhenChanged?: boolean;
};

function TaskFormBody({ title, defaults, originalDeadline, submitLabel, submit, top, hint, onlyWhenChanged }: BodyProps) {
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const { control, handleSubmit, setError, setValue, formState } = useForm<TaskFormInput, unknown, TaskFormOutput>({
    resolver: zodResolver(taskFormSchemaFor(originalDeadline)),
    mode: 'onBlur',
    defaultValues: defaults,
  });

  const save = handleSubmit(async (form) => {
    setFormError(null);
    try {
      await submit(form);
    } catch (e) {
      if (!showFieldErrors(e, setError)) setFormError(e instanceof ApiError ? e.message : strings.errors.SERVER);
    }
  });

  return (
    <Screen edges={['bottom']}>
      <Stack.Screen options={{ title }} />
      {top}

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
                      setValue('category', c, { shouldValidate: true, shouldDirty: true });
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
            setValue('hours', String(next), { shouldValidate: true, shouldDirty: true });
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
          <DeadlineField
            label={strings.create.deadlineLabel}
            value={field.value}
            onChange={(d) => setValue('deadline', d, { shouldValidate: true, shouldDirty: true })}
            error={fieldState.error?.message}
          />
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

      <AppText variant="caption">{hint}</AppText>
      <Button label={submitLabel} onPress={save} loading={formState.isSubmitting} disabled={onlyWhenChanged && !formState.isDirty} />
      {formError ? <AppText style={[styles.error, styles.center]}>{formError}</AppText> : null}
    </Screen>
  );
}

// Per-field messages from the server ("Pick a time in the future." etc.) go under their fields
function showFieldErrors(e: unknown, setError: UseFormSetError<TaskFormInput>): boolean {
  if (!isApiError(e, 'VALIDATION') || !Object.keys(e.fieldErrors).length) return false;
  for (const [field, message] of Object.entries(e.fieldErrors)) {
    const name = SERVER_FIELDS[field];
    if (name) setError(name, { message });
  }
  return true;
}

function StepButton({ label, accessibilityLabel, onPress }: { label: string; accessibilityLabel: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={accessibilityLabel} style={styles.step}>
      <AppText style={styles.stepText}>{label}</AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  loading: { marginTop: space.xxl },
  field: { gap: space.xs },
  locked: { backgroundColor: colors.neutralChipBg, borderRadius: radius.input, padding: space.m, gap: 2 },
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
