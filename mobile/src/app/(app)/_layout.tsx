import { Stack } from 'expo-router';

import { strings } from '@/strings/en';
import { colors, fonts } from '@/theme/tokens';

// Signed-in area: the tabs, with full screens that open on top of them (UI plan 5.1)
export default function AppLayout() {
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
    </Stack>
  );
}
