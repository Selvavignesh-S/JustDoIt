import { Feather } from '@expo/vector-icons';
import { Tabs } from 'expo-router';

import { strings } from '@/strings/en';
import { colors, fonts } from '@/theme/tokens';

// Signed-in area: bottom tabs Tasks · Completed · Settings (UI plan 5.1)
export default function AppLayout() {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.brand,
        tabBarInactiveTintColor: colors.muted,
        tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.line },
        tabBarLabelStyle: { fontFamily: fonts.bodySemi, fontSize: 12 },
        sceneStyle: { backgroundColor: colors.bg },
      }}>
      <Tabs.Screen
        name="index"
        options={{ title: strings.tabs.tasks, tabBarIcon: ({ color, size }) => <Feather name="check-square" color={color as string} size={size} /> }}
      />
      <Tabs.Screen
        name="completed"
        options={{ title: strings.tabs.completed, tabBarIcon: ({ color, size }) => <Feather name="check-circle" color={color as string} size={size} /> }}
      />
      <Tabs.Screen
        name="settings"
        options={{ title: strings.tabs.settings, tabBarIcon: ({ color, size }) => <Feather name="settings" color={color as string} size={size} /> }}
      />
    </Tabs>
  );
}
