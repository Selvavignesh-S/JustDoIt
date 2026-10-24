// Reminders outside the app (decided 2026-10-25): the server pushes them through Expo; this file gets the
// phone's push token to the server and creates the Android channel they arrive on.
import Constants, { ExecutionEnvironment } from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import { request } from '@/api/client';
import { strings } from '@/strings/en';

export type ReminderStatus = 'on' | 'off' | 'unavailable';

// A reminder that arrives while the app is open still shows as a banner
Notifications.setNotificationHandler({
  handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
});

let registeredToken: string | null = null;

// ask=false only reads the state (Settings); ask=true may show the system permission prompt.
// 'unavailable': web preview, an emulator, Expo Go on Android (it can't receive pushes since SDK 53),
// or a build without an EAS project id. None of these is an error the user can fix.
export async function enableReminders(ask: boolean): Promise<ReminderStatus> {
  if (Platform.OS === 'web' || !Device.isDevice) return 'unavailable';
  if (Platform.OS === 'android' && Constants.executionEnvironment === ExecutionEnvironment.StoreClient) return 'unavailable';
  if (Platform.OS === 'android') {
    // The server sends to this channel id; users can mute it on its own in system settings
    await Notifications.setNotificationChannelAsync('reminders', {
      name: strings.reminders.channel,
      // eslint-disable-next-line import/namespace -- re-exported through `export *`, which the lint rule can't follow
      importance: Notifications.AndroidImportance.HIGH,
    });
  }
  let permission = await Notifications.getPermissionsAsync();
  if (!permission.granted && ask && permission.canAskAgain) permission = await Notifications.requestPermissionsAsync();
  if (!permission.granted) return 'off';

  const projectId = (Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId) as string | undefined;
  if (!projectId) return 'unavailable';
  try {
    const { data: token } = await Notifications.getExpoPushTokenAsync({ projectId });
    await request<void>('/users/me/push-token', { method: 'PUT', body: { token } });
    registeredToken = token;
    return 'on';
  } catch {
    return 'unavailable';
  }
}

// Manual log out: this phone stops getting this account's reminders. Best effort and not awaited, so a
// sleeping server never holds up log out; the request already carries the sign-in header when it starts.
export function stopReminders() {
  const token = registeredToken;
  registeredToken = null;
  if (token) request<void>('/users/me/push-token', { method: 'DELETE', body: { token } }).catch(() => {});
}
