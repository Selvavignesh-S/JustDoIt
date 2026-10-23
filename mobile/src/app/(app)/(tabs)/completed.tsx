import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { isApiError } from '@/api/errors';
import { deleteTask, listTasks } from '@/api/tasks';
import { Task, TaskSort } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Sheet, SheetItem } from '@/components/Sheet';
import { useSnackbar } from '@/components/Snackbar';
import { TaskCard } from '@/components/TaskCard';
import { strings } from '@/strings/en';
import { refreshTaskData } from '@/tasks/cache';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';

// S11 Completed (UI plan 7.12): the same list as the dashboard with status=COMPLETED.
// No ladder, no create button, no scope chips; the card menu offers only Delete.
// No "completed on" date or "recently updated" sort: the API has neither (UI decision D12).
export default function CompletedScreen() {
  const queryClient = useQueryClient();
  const showSnack = useSnackbar();
  const [sort, setSort] = useState<TaskSort>('PRIORITY');
  const [sortOpen, setSortOpen] = useState(false);
  const [menuTask, setMenuTask] = useState<Task | null>(null);
  const [deleting, setDeleting] = useState<Task | null>(null);
  const [conflict, setConflict] = useState(false);

  const filters = { status: 'COMPLETED' as const, sort };
  const list = useInfiniteQuery({
    queryKey: ['tasks', filters],
    queryFn: ({ pageParam }) => listTasks(filters, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  });
  const tasks = list.data?.pages.flatMap((p) => p.items) ?? [];

  const remove = useMutation({
    mutationFn: (t: Task) => deleteTask(t.id, t.version),
    onSuccess: () => {
      setDeleting(null);
      void refreshTaskData(queryClient);
      showSnack({ message: strings.tasks.deleted });
    },
    onError: (e) => {
      setDeleting(null);
      if (isApiError(e, 'CONFLICT') || isApiError(e, 'NOT_AVAILABLE')) setConflict(true);
      else showSnack({ message: e instanceof Error ? e.message : strings.errors.SERVER });
    },
  });

  const header = (
    <View style={styles.header}>
      <AppText variant="heading">{strings.completed.title}</AppText>
      <Pressable onPress={() => setSortOpen(true)} accessibilityRole="button" accessibilityLabel={sort === 'PRIORITY' ? strings.tasks.sortPriority : strings.tasks.sortDeadline} style={styles.pill}>
        <AppText style={styles.pillText}>{sort === 'PRIORITY' ? strings.tasks.sortPriority : strings.tasks.sortDeadline} ▾</AppText>
      </Pressable>
    </View>
  );

  const empty = list.isPending ? (
    <ActivityIndicator style={styles.footer} color={colors.brand} />
  ) : list.isError ? (
    <View style={styles.gap}>
      <Banner tone="error" message={list.error.message} />
      <Button variant="secondary" label={strings.common.retry} onPress={() => list.refetch()} />
    </View>
  ) : (
    <AppText style={styles.empty}>{strings.completed.empty}</AppText>
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
        renderItem={({ item }) => <TaskCard task={item} onMenu={() => setMenuTask(item)} onPress={() => router.push(`/task/${item.id}`)} />}
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
        refreshControl={<RefreshControl refreshing={list.isRefetching && !list.isFetchingNextPage} onRefresh={() => void refreshTaskData(queryClient)} tintColor={colors.brand} />}
      />

      <Sheet visible={sortOpen} onClose={() => setSortOpen(false)} title={strings.tasks.sortTitle}>
        {(['PRIORITY', 'DEADLINE'] as const).map((s) => (
          <SheetItem
            key={s}
            label={s === 'PRIORITY' ? strings.tasks.byPriority : strings.tasks.byDeadline}
            selected={sort === s}
            onPress={() => {
              setSort(s);
              setSortOpen(false);
            }}
          />
        ))}
      </Sheet>

      <Sheet visible={!!menuTask} onClose={() => setMenuTask(null)} title={menuTask?.title}>
        <SheetItem label={strings.tasks.deleteTask} destructive onPress={() => (setDeleting(menuTask), setMenuTask(null))} />
      </Sheet>

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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  content: { padding: space.l, paddingBottom: space.xxl * 2 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: space.m, gap: space.m },
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
  separator: { height: space.s },
  gap: { gap: space.s },
  empty: { textAlign: 'center', paddingVertical: space.xl, color: colors.muted },
  footer: { paddingVertical: space.l },
  footerRow: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: space.s },
});
