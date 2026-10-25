import { useQueryClient } from '@tanstack/react-query';
import { router, Stack } from 'expo-router';
import { useEffect } from 'react';

import { enableReminders, listenForReminders } from '@/notifications/reminders';
import { strings } from '@/strings/en';
import { refreshTaskData } from '@/tasks/cache';
import { colors, fonts } from '@/theme/tokens';

// Signed-in area: the tabs, with full screens that open on top of them (UI plan 5.1)
export default function AppLayout() {
  const queryClient = useQueryClient();

  // Signed in: ask once for notification permission and hand this phone's push token to the server
  useEffect(() => {
    void enableReminders(true);
  }, []);

  // A reminder that arrives while the app is open refreshes the banner and counts; tapping one opens its task
  useEffect(
    () =>
      listenForReminders(
        (taskId) => router.push(`/task/${taskId}`),
        () => void refreshTaskData(queryClient),
      ),
    [queryClient],
  );

  return (
    <Stack
      screenOptions={{
        headerTintColor: colors.brand,
        headerTitleStyle: { fontFamily: fonts.headingSemi, color: colors.ink },
        headerStyle: { backgroundColor: colors.bg },
        headerShadowVisible: false,
        contentStyle: { backgroundColor: colors.bg },
      }}>
      {/* The title is what the back button on the screens above shows */}
      <Stack.Screen name="(tabs)" options={{ headerShown: false, title: strings.tabs.tasks }} />
      <Stack.Screen name="new-task" options={{ title: strings.create.title }} />
      <Stack.Screen name="task-form" options={{ title: strings.create.reviewTitle }} />
      <Stack.Screen name="task/[id]" options={{ title: '' }} />
    </Stack>
  );
}
