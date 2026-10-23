import { Feather } from '@expo/vector-icons';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { isApiError } from '@/api/errors';
import { completeTask, deleteTask, getReminders, getSummary, listTasks, reopenTask, rescheduleTask } from '@/api/tasks';
import { CATEGORIES, Category, Priority, PRIORITIES, Task, TaskScope, TaskSort } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { PriorityLadder } from '@/components/PriorityLadder';
import { RescheduleSheet } from '@/components/RescheduleSheet';
import { Sheet, SheetItem } from '@/components/Sheet';
import { useSnackbar } from '@/components/Snackbar';
import { TaskCard } from '@/components/TaskCard';
import { strings } from '@/strings/en';
import { refreshTaskData } from '@/tasks/cache';
import { colors, fonts, minTouch, radius, space, type } from '@/theme/tokens';

type OpenSheet = null | 'sort' | 'category' | 'reminders';

// S3 Tasks dashboard (UI plan 7.5): answers "what do I do next?" in the first screenful.
// Overdue, due soon, priority and order all come from the server; the phone only displays them.
export default function TasksScreen() {
  const queryClient = useQueryClient();
  const showSnack = useSnackbar();

  const [priority, setPriority] = useState<Priority | null>(null);
  const [category, setCategory] = useState<Category | null>(null);
  const [scope, setScope] = useState<TaskScope | null>(null); // null = all pending
  const [sort, setSort] = useState<TaskSort>('PRIORITY');
  const [openSheet, setOpenSheet] = useState<OpenSheet>(null);
  const [menuTask, setMenuTask] = useState<Task | null>(null);
  const [rescheduling, setRescheduling] = useState<Task | null>(null);
  const [deleting, setDeleting] = useState<Task | null>(null);
  const [conflict, setConflict] = useState(false);
  const [bannerHiddenAt, setBannerHiddenAt] = useState<number | null>(null);

  // Any filter, sort or scope change gives a new query key, so paging restarts at page 0
  const filters = { status: 'PENDING' as const, priority, category, scope, sort };
  const list = useInfiniteQuery({
    queryKey: ['tasks', filters],
    queryFn: ({ pageParam }) => listTasks(filters, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  });
  const summary = useQuery({ queryKey: ['summary'], queryFn: getSummary });
  const reminders = useQuery({ queryKey: ['reminders'], queryFn: getReminders });
  const tasks = list.data?.pages.flatMap((p) => p.items) ?? [];

  const onError = (e: unknown) => {
    // A stale row version: another device changed this task first (UI plan 8.2)
    if (isApiError(e, 'CONFLICT') || isApiError(e, 'NOT_AVAILABLE')) setConflict(true);
    else showSnack({ message: e instanceof Error ? e.message : strings.errors.SERVER });
  };

  const reopen = useMutation({
    mutationFn: (t: Task) => reopenTask(t.id, t.version),
    onSuccess: () => refreshTaskData(queryClient),
    onError,
  });
  const complete = useMutation({
    mutationFn: (t: Task) => completeTask(t.id, t.version),
    onSuccess: (done) => {
      void refreshTaskData(queryClient);
      // Undo reopens with the NEW row version the complete call returned (UI plan 7.5, 8.2)
      showSnack({ message: strings.tasks.completed, actionLabel: strings.tasks.undo, onAction: () => reopen.mutate(done) });
    },
    onError,
  });
  const reschedule = useMutation({
    mutationFn: ({ task, deadline }: { task: Task; deadline: Date }) => rescheduleTask(task.id, deadline.toISOString(), task.version),
    onSuccess: () => {
      setRescheduling(null);
      void refreshTaskData(queryClient);
    },
    onError: (e) => {
      setRescheduling(null);
      onError(e);
    },
  });
  const remove = useMutation({
    mutationFn: (t: Task) => deleteTask(t.id, t.version),
    onSuccess: () => {
      setDeleting(null);
      void refreshTaskData(queryClient);
      // No Undo: deletion is permanent (SRS 9.5)
      showSnack({ message: strings.tasks.deleted });
    },
    onError: (e) => {
      setDeleting(null);
      onError(e);
    },
  });

  const filtered = priority !== null || category !== null || scope !== null;
  const clearFilters = () => {
    setPriority(null);
    setCategory(null);
    setScope(null);
  };
  const pendingTotal = summary.data ? PRIORITIES.reduce((n, p) => n + summary.data.pending[p], 0) : undefined;
  const firstReminder = reminders.data?.[0];
  const showBanner = firstReminder && bannerHiddenAt !== reminders.dataUpdatedAt;

  const header = (
    <View style={styles.header}>
      <View style={styles.titleRow}>
        <AppText variant="heading">{strings.tasks.title}</AppText>
        <Pressable
          onPress={() => setOpenSheet('reminders')}
          accessibilityRole="button"
          accessibilityLabel={`${strings.tasks.reminders}, ${reminders.data?.length ?? 0}`}
          hitSlop={8}
          style={styles.bell}>
          <Feather name="bell" size={20} color={colors.brand} />
          <AppText style={styles.bellText}>
            {strings.tasks.reminders} {reminders.data?.length ? `(${reminders.data.length})` : ''}
          </AppText>
        </Pressable>
      </View>

      {showBanner ? (
        <View style={styles.banner}>
          <AppText style={styles.bannerText}>
            {firstReminder.title}: {firstReminder.reason}
          </AppText>
          <View style={styles.bannerActions}>
            <Button variant="link" label={strings.tasks.view} onPress={() => router.push(`/task/${firstReminder.taskId}`)} />
            {/* Closing only hides it until the next sync; nothing is sent to the server (SRS 13) */}
            <Button variant="link" label={strings.tasks.close} onPress={() => setBannerHiddenAt(reminders.dataUpdatedAt)} />
          </View>
        </View>
      ) : null}

      <PriorityLadder counts={summary.data?.pending} selected={priority} onSelect={setPriority} />
      <Button label={strings.tasks.create} onPress={() => router.push('/new-task')} />

      <View style={styles.pills}>
        <Pill label={sort === 'PRIORITY' ? strings.tasks.sortPriority : strings.tasks.sortDeadline} onPress={() => setOpenSheet('sort')} />
        <Pill label={category ? `${strings.tasks.categoryTitle}: ${strings.category[category]}` : strings.tasks.categoryAll} onPress={() => setOpenSheet('category')} />
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.scopes}>
        <ScopeChip label={strings.tasks.pending} count={pendingTotal} selected={scope === null} onPress={() => setScope(null)} />
        <ScopeChip label={strings.tasks.dueSoon} count={summary.data?.dueSoon} selected={scope === 'DUE_SOON'} onPress={() => setScope('DUE_SOON')} />
        <ScopeChip label={strings.tasks.overdue} count={summary.data?.overdue} selected={scope === 'OVERDUE'} onPress={() => setScope('OVERDUE')} />
      </ScrollView>
    </View>
  );

  const empty = list.isPending ? (
    <View style={styles.gap}>
      {[0, 1, 2].map((i) => (
        <View key={i} style={styles.skeleton} />
      ))}
    </View>
  ) : list.isError ? (
    <View style={styles.gap}>
      <Banner tone="error" message={list.error.message} />
      <Button variant="secondary" label={strings.common.retry} onPress={() => list.refetch()} />
    </View>
  ) : filtered ? (
    <View style={styles.empty}>
      <AppText>{strings.tasks.emptyFiltered}</AppText>
      <Button variant="link" label={strings.tasks.clearFilters} onPress={clearFilters} />
    </View>
  ) : (
    <View style={styles.empty}>
      <AppText>{strings.tasks.emptyAccount}</AppText>
    </View>
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <FlatList
        data={tasks}
        keyExtractor={(t) => String(t.id)}
        contentContainerStyle={styles.content}
        ListHeaderComponent={header}
        ListEmptyComponent={empty}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        renderItem={({ item }) => (
          <TaskCard
            task={item}
            completing={complete.isPending && complete.variables?.id === item.id}
            onComplete={() => complete.mutate(item)}
            onMenu={() => setMenuTask(item)}
            onPress={() => router.push(`/task/${item.id}`)}
          />
        )}
        onEndReached={() => list.hasNextPage && !list.isFetchingNextPage && !list.isFetchNextPageError && list.fetchNextPage()}
        onEndReachedThreshold={0.5}
        ListFooterComponent={
          list.isFetchingNextPage ? (
            <ActivityIndicator style={styles.footer} color={colors.brand} />
          ) : list.isFetchNextPageError ? (
            <View style={[styles.footer, styles.footerRow]}>
              <AppText>{strings.tasks.loadMoreFailed}</AppText>
              <Button variant="link" label={strings.common.retry} onPress={() => list.fetchNextPage()} />
            </View>
          ) : undefined
        }
        refreshControl={
          <RefreshControl refreshing={list.isRefetching && !list.isFetchingNextPage} onRefresh={() => void refreshTaskData(queryClient)} tintColor={colors.brand} />
        }
      />

      <Sheet visible={openSheet === 'sort'} onClose={() => setOpenSheet(null)} title={strings.tasks.sortTitle}>
        {(['PRIORITY', 'DEADLINE'] as const).map((s) => (
          <SheetItem
            key={s}
            label={s === 'PRIORITY' ? strings.tasks.byPriority : strings.tasks.byDeadline}
            selected={sort === s}
            onPress={() => {
              setSort(s);
              setOpenSheet(null);
            }}
          />
        ))}
      </Sheet>

      <Sheet visible={openSheet === 'category'} onClose={() => setOpenSheet(null)} title={strings.tasks.categoryTitle}>
        <ScrollView>
          <SheetItem label={strings.tasks.all} selected={category === null} onPress={() => (setCategory(null), setOpenSheet(null))} />
          {CATEGORIES.map((c) => (
            <SheetItem key={c} label={strings.category[c]} selected={category === c} onPress={() => (setCategory(c), setOpenSheet(null))} />
          ))}
        </ScrollView>
      </Sheet>

      <Sheet visible={openSheet === 'reminders'} onClose={() => setOpenSheet(null)} title={strings.tasks.reminders}>
        <ScrollView>
          {reminders.data?.length ? (
            reminders.data.map((r) => (
              // Tap opens the task (UI plan 7.13)
              <Pressable
                key={r.taskId}
                accessibilityRole="button"
                onPress={() => {
                  setOpenSheet(null);
                  router.push(`/task/${r.taskId}`);
                }}
                style={styles.reminderRow}>
                <AppText variant="bodyStrong">{r.title}</AppText>
                <AppText variant="caption">{r.reason}</AppText>
              </Pressable>
            ))
          ) : (
            <AppText variant="caption">{strings.tasks.noReminders}</AppText>
          )}
        </ScrollView>
      </Sheet>

      <Sheet visible={!!menuTask} onClose={() => setMenuTask(null)} title={menuTask?.title}>
        <SheetItem label={strings.tasks.reschedule} onPress={() => (setRescheduling(menuTask), setMenuTask(null))} />
        <SheetItem label={strings.tasks.deleteTask} destructive onPress={() => (setDeleting(menuTask), setMenuTask(null))} />
      </Sheet>

      <RescheduleSheet
        key={rescheduling?.id ?? 'none'}
        task={rescheduling}
        saving={reschedule.isPending}
        onClose={() => setRescheduling(null)}
        onSave={(deadline) => rescheduling && reschedule.mutate({ task: rescheduling, deadline })}
      />

      <ConfirmDialog
        visible={!!deleting}
        title={deleting ? strings.tasks.deleteTitle(deleting.title) : ''}
        body={strings.tasks.deleteBody}
        confirmLabel={strings.tasks.deleteTask}
        destructive
        loading={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onCancel={() => setDeleting(null)}
      />

      <ConfirmDialog
        visible={conflict}
        title={strings.tasks.conflictTitle}
        body={strings.tasks.conflictBody}
        confirmLabel={strings.tasks.reload}
        onConfirm={() => {
          setConflict(false);
          void refreshTaskData(queryClient);
        }}
      />
    </SafeAreaView>
  );
}

function Pill({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label} style={styles.pill}>
      <AppText style={styles.pillText}>{label} ▾</AppText>
    </Pressable>
  );
}

// Exclusive: one selected at a time. Counts are all pending tasks, never the filtered list (Route plan 3.4)
function ScopeChip({ label, count, selected, onPress }: { label: string; count?: number; selected: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={`${label} ${count ?? ''}`.trim()}
      style={[styles.scope, selected && styles.scopeSelected]}>
      <AppText style={[type.label, selected && { color: colors.white }]}>
        {label} {count ?? ''}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  content: { padding: space.l, paddingBottom: space.xxl * 2 },
  header: { gap: space.m, marginBottom: space.m },
  titleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  bell: { flexDirection: 'row', alignItems: 'center', gap: space.xs, minHeight: minTouch },
  bellText: { color: colors.brand, fontFamily: fonts.bodySemi },
  banner: { backgroundColor: colors.brandTint, borderRadius: radius.input, padding: space.m, gap: space.xs },
  bannerText: { color: colors.infoInk },
  bannerActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: space.m },
  pills: { flexDirection: 'row', gap: space.s, flexWrap: 'wrap' },
  pill: {
    minHeight: minTouch - 8,
    justifyContent: 'center',
    paddingHorizontal: space.m,
    borderWidth: 1,
    borderColor: colors.inputBorder,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
  },
  pillText: { fontFamily: fonts.bodySemi, fontSize: 13 },
  scopes: { gap: space.s },
  scope: {
    minHeight: minTouch - 8,
    justifyContent: 'center',
    paddingHorizontal: space.m,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.inputBorder,
    backgroundColor: colors.surface,
  },
  scopeSelected: { backgroundColor: colors.brand, borderColor: colors.brand },
  separator: { height: space.s },
  gap: { gap: space.s },
  skeleton: { height: 96, borderRadius: radius.card, backgroundColor: colors.line },
  empty: { alignItems: 'center', gap: space.s, paddingVertical: space.xl },
  footer: { paddingVertical: space.l },
  footerRow: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: space.s },
  reminderRow: { paddingVertical: space.s, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 2 },
});
