import { BricolageGrotesque_600SemiBold, BricolageGrotesque_700Bold } from '@expo-google-fonts/bricolage-grotesque';
import { Figtree_400Regular, Figtree_500Medium, Figtree_600SemiBold, Figtree_700Bold } from '@expo-google-fonts/figtree';
import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Platform } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { SessionProvider, useSession } from '@/auth/session';
import { SnackbarProvider } from '@/components/Snackbar';
import { WakingNote } from '@/components/WakingNote';
import { strings } from '@/strings/en';
import { colors, fonts } from '@/theme/tokens';

// Keep the splash up until fonts and the stored session are loaded
SplashScreen.preventAutoHideAsync();

// Coming back to the app refetches what's on screen: another device may have changed it (UI plan 7.5, 8.3)
function useRefetchOnForeground() {
  useEffect(() => {
    if (Platform.OS === 'web') return;
    const sub = AppState.addEventListener('change', (state) => focusManager.setFocused(state === 'active'));
    return () => sub.remove();
  }, []);
}

export default function RootLayout() {
  // Server data lives only in this in-memory cache, never on the device (UI plan 2, 8.3).
  // No React Query retries: the API client already retries a failed GET once.
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 }, mutations: { retry: false } } }),
  );
  useRefetchOnForeground();
  const [fontsLoaded, fontError] = useFonts({
    BricolageGrotesque_600SemiBold,
    BricolageGrotesque_700Bold,
    Figtree_400Regular,
    Figtree_500Medium,
    Figtree_600SemiBold,
    Figtree_700Bold,
  });

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <SnackbarProvider>
            {/* If the fonts fail to load the app still starts, with system fonts */}
            <RootNavigator fontsReady={fontsLoaded || !!fontError} />
            <WakingNote />
            <StatusBar style="dark" />
          </SnackbarProvider>
        </SessionProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}

function RootNavigator({ fontsReady }: { fontsReady: boolean }) {
  const { token, isLoading } = useSession();
  const ready = fontsReady && !isLoading;

  useEffect(() => {
    if (ready) SplashScreen.hide();
  }, [ready]);

  if (!ready) return null;

  // Signed in: only the app tabs exist. Signed out: only sign in (S1), sign up (S2) and forgot password (S1a).
  // Signing in or out flips the guards and the router moves to the first screen that became available.
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        headerTintColor: colors.brand,
        headerTitleStyle: { fontFamily: fonts.headingSemi, color: colors.ink },
        headerStyle: { backgroundColor: colors.bg },
        headerShadowVisible: false,
        contentStyle: { backgroundColor: colors.bg },
      }}>
      <Stack.Protected guard={!!token}>
        <Stack.Screen name="(app)" />
      </Stack.Protected>
      <Stack.Protected guard={!token}>
        <Stack.Screen name="sign-in" />
        <Stack.Screen name="sign-up" options={{ headerShown: true, title: strings.signUp.title }} />
        <Stack.Screen name="forgot-password" options={{ headerShown: true, title: strings.forgot.title }} />
      </Stack.Protected>
      {/* The emailed confirmation link works signed in or out (UI plan 5.2) */}
      <Stack.Screen name="confirm-email" />
    </Stack>
  );
}
