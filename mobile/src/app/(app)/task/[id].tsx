import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { isApiError } from '@/api/errors';
import { completeTask, deleteTask, getTask, reopenTask, rescheduleTask } from '@/api/tasks';
import { Task } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { Chip, OverdueChip, PriorityChip } from '@/components/Chips';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { RescheduleSheet } from '@/components/RescheduleSheet';
import { Screen } from '@/components/Screen';
import { TaskFiles } from '@/components/TaskFiles';
import { useSnackbar } from '@/components/Snackbar';
import { strings } from '@/strings/en';
import { refreshTaskData } from '@/tasks/cache';
import { colors, radius, space } from '@/theme/tokens';
import { formatDateTime, formatHours, formatShortDate } from '@/utils/format';

// S7 Task detail (UI plan 7.9). Completed tasks are read-only: no Edit, no Reschedule (SRS 9.5).
export default function TaskDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const taskId = Number(id);
  const queryClient = useQueryClient();
  const showSnack = useSnackbar();
  const [rescheduling, setRescheduling] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [conflict, setConflict] = useState(false);

  // Opened from a link with no history (web URL, later a notification): "back" goes to the list
  const leave = () => (router.canGoBack() ? router.back() : router.replace('/'));

  const query = useQuery({ queryKey: ['task', taskId], queryFn: () => getTask(taskId) });
  const task = query.data;

  // Every change returns the task with its NEW row version; keep that (plus the file list, which only
  // GET /tasks/{id} returns) so the next action sends the right version (UI plan 8.2)
  const keep = (updated: Task) => {
    queryClient.setQueryData<Task>(['task', taskId], (old) => ({ ...updated, attachments: old?.attachments }));
    void refreshTaskData(queryClient);
  };
  const onError = (e: unknown) => {
    if (isApiError(e, 'CONFLICT') || isApiError(e, 'TASK_COMPLETED')) setConflict(true);
    else showSnack({ message: e instanceof Error ? e.message : strings.errors.SERVER });
  };

  const reopen = useMutation({ mutationFn: (t: Task) => reopenTask(t.id, t.version), onSuccess: keep, onError });
  const complete = useMutation({
    mutationFn: (t: Task) => completeTask(t.id, t.version),
    onSuccess: (done) => {
      keep(done);
      // Back to the list with the same Undo as the card's check circle (UI plan 7.9)
      leave();
      showSnack({ message: strings.tasks.completed, actionLabel: strings.tasks.undo, onAction: () => reopen.mutate(done) });
    },
    onError,
  });
  const reschedule = useMutation({
    mutationFn: ({ t, deadline }: { t: Task; deadline: Date }) => rescheduleTask(t.id, deadline.toISOString(), t.version),
    onSuccess: (updated) => {
      setRescheduling(false);
      keep(updated);
    },
    onError: (e) => {
      setRescheduling(false);
      onError(e);
    },
  });
  const remove = useMutation({
    mutationFn: (t: Task) => deleteTask(t.id, t.version),
    onSuccess: () => {
      setConfirmDelete(false);
      queryClient.removeQueries({ queryKey: ['task', taskId] });
      void refreshTaskData(queryClient);
      leave();
      showSnack({ message: strings.tasks.deleted });
    },
    onError: (e) => {
      setConfirmDelete(false);
      onError(e);
    },
  });

  if (query.isPending) {
    return <ActivityIndicator style={styles.loading} color={colors.brand} />;
  }
  if (query.isError || !task) {
    // "Not found" and "not yours" look the same, so ids can't be probed (UI plan rule 7)
    const notAvailable = isApiError(query.error, 'NOT_AVAILABLE');
    return (
      <Screen edges={['bottom']}>
        {/* The task may have been deleted while open: drop its Edit button */}
        <Stack.Screen options={{ headerRight: undefined }} />
        <Banner tone={notAvailable ? 'warning' : 'error'} message={notAvailable ? strings.detail.notAvailable : (query.error?.message ?? strings.errors.SERVER)} />
        {notAvailable ? null : <Button variant="secondary" label={strings.common.retry} onPress={() => query.refetch()} />}
        <Button variant="secondary" label={strings.detail.goBack} onPress={leave} />
      </Screen>
    );
  }

  const pending = task.status === 'PENDING';

  return (
    <Screen edges={['bottom']}>
      <Stack.Screen
        options={{
          title: '',
          headerRight: pending
            ? () => <Button variant="link" label={strings.detail.edit} onPress={() => router.push({ pathname: '/task-form', params: { mode: 'edit', id: String(task.id) } })} />
            : undefined,
        }}
      />

      <View style={styles.block}>
        <AppText variant="heading">{task.title}</AppText>
        <View style={styles.chips}>
          <Chip label={strings.category[task.category]} />
          <PriorityChip priority={task.currentPriority} muted={!pending} />
          {task.overdue ? <OverdueChip /> : null}
        </View>
        {/* initialPriority never changes; it can be "Due tomorrow" for a task created under 24 h before its deadline */}
        <AppText variant="caption">{strings.detail.startedAs(strings.priority[task.initialPriority])}</AppText>
      </View>

      <View style={styles.card}>
        <Row label={strings.detail.deadline}>
          <View style={styles.rowValue}>
            <AppText>{formatDateTime(task.deadline)}</AppText>
            {pending ? <Button variant="link" label={strings.tasks.reschedule} onPress={() => setRescheduling(true)} style={styles.inlineLink} /> : null}
          </View>
        </Row>
        <Row label={strings.detail.effort}>
          <AppText>{formatHours(task.estimatedHours, true)}</AppText>
        </Row>
        <Row label={strings.detail.status}>
          <AppText>{pending ? strings.detail.pending : strings.detail.completedStatus}</AppText>
        </Row>
        <Row label={strings.detail.created} last>
          <AppText>{formatShortDate(task.createdAt)}</AppText>
        </Row>
      </View>

      {task.description ? (
        <View style={[styles.card, styles.padded]}>
          <AppText variant="label">{strings.detail.description}</AppText>
          <AppText>{task.description}</AppText>
        </View>
      ) : null}

      <TaskFiles taskId={task.id} files={task.attachments ?? []} />

      <View style={styles.actions}>
        {pending ? (
          <Button label={strings.detail.markComplete} onPress={() => complete.mutate(task)} loading={complete.isPending} />
        ) : (
          // No confirmation: the task simply returns to Pending and its priority resumes updating (UI plan 7.9)
          <Button label={strings.detail.markPending} onPress={() => reopen.mutate(task)} loading={reopen.isPending} />
        )}
        <Button variant="destructive" label={strings.tasks.deleteTask} onPress={() => setConfirmDelete(true)} />
      </View>

      <RescheduleSheet
        key={rescheduling ? `open-${task.version}` : 'closed'}
        task={rescheduling ? task : null}
        saving={reschedule.isPending}
        onClose={() => setRescheduling(false)}
        onSave={(deadline) => reschedule.mutate({ t: task, deadline })}
      />
      <ConfirmDialog
        visible={confirmDelete}
        title={strings.tasks.deleteTitle(task.title)}
        body={strings.tasks.deleteBody}
        confirmLabel={strings.tasks.deleteTask}
        destructive
        loading={remove.isPending}
        onConfirm={() => remove.mutate(task)}
        onCancel={() => setConfirmDelete(false)}
      />
      <ConfirmDialog
        visible={conflict}
        title={strings.tasks.conflictTitle}
        body={strings.tasks.conflictBody}
        confirmLabel={strings.tasks.reload}
        onConfirm={() => {
          setConflict(false);
          void query.refetch();
          void refreshTaskData(queryClient);
        }}
      />
    </Screen>
  );
}

function Row({ label, children, last }: { label: string; children: React.ReactNode; last?: boolean }) {
  return (
    <View style={[styles.row, !last && styles.rowDivider]}>
      <AppText variant="caption" style={styles.rowLabel}>
        {label}
      </AppText>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  loading: { marginTop: space.xxl },
  block: { gap: space.s },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: space.xs },
  card: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderRadius: radius.card, paddingHorizontal: space.l },
  padded: { paddingVertical: space.m, gap: space.xs },
  row: { paddingVertical: space.m, gap: 2 },
  rowDivider: { borderBottomWidth: 1, borderBottomColor: colors.line },
  rowLabel: { marginBottom: 2 },
  rowValue: { alignItems: 'flex-start' },
  inlineLink: { alignSelf: 'flex-start', paddingHorizontal: 0 },
  actions: { gap: space.s, marginTop: space.s },
});
