// Reminders outside the app (decided 2026-10-25): the server pushes them through Expo; this file gets the
// phone's push token to the server, creates the Android channel they arrive on, and reacts to taps.
import { isRunningInExpoGo } from 'expo';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { Platform } from 'react-native';

import { request } from '@/api/client';
import { strings } from '@/strings/en';

export type ReminderStatus = 'on' | 'off' | 'unavailable';

// Expo Go on Android has no push module since SDK 53, and merely importing expo-notifications there crashes
// the app ("Cannot find native module 'ExpoTopicSubscriptionModule'"). The web preview has no notifications.
// So the package is loaded only where pushes can work: the installed app (and Expo Go on iOS).
// isRunningInExpoGo() is the check expo-notifications itself uses; Constants.executionEnvironment didn't say
// "storeClient" in Expo Go on a real Android phone (2026-10-25), so it can't be trusted for this.
const pushCapable = Platform.OS !== 'web' && !(Platform.OS === 'android' && isRunningInExpoGo());
// eslint-disable-next-line @typescript-eslint/no-require-imports -- a conditional load; a static import would crash Expo Go
const Notifications: typeof import('expo-notifications') | null = pushCapable ? require('expo-notifications') : null;

// A reminder that arrives while the app is open still shows as a banner
Notifications?.setNotificationHandler({
  handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
});

let registeredToken: string | null = null;

// ask=false only reads the state (Settings); ask=true may show the system permission prompt.
// 'unavailable': web preview, an emulator, Expo Go on Android, or a build without an EAS project id.
// None of these is an error the user can fix.
export async function enableReminders(ask: boolean): Promise<ReminderStatus> {
  if (!Notifications || !Device.isDevice) return 'unavailable';
  if (Platform.OS === 'android') {
    // The server sends to this channel id; users can mute it on its own in system settings
    await Notifications.setNotificationChannelAsync('reminders', {
      name: strings.reminders.channel,
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

// Signed-in area only. A tap opens its task (a summary of several has no taskId and just opens the app); a tap
// that started the app while signed out is handled once this runs after sign-in. Returns the unsubscribe.
export function listenForReminders(onTapTask: (taskId: number) => void, onArrive: () => void): () => void {
  const N = Notifications;
  if (!N) return () => {};
  const open = (tapped: import('expo-notifications').NotificationResponse | null) => {
    if (!tapped) return;
    N.clearLastNotificationResponse();
    const taskId = tapped.notification.request.content.data?.taskId;
    if (typeof taskId === 'number') onTapTask(taskId);
  };
  open(N.getLastNotificationResponse());
  const taps = N.addNotificationResponseReceivedListener(open);
  const arrivals = N.addNotificationReceivedListener(onArrive);
  return () => {
    taps.remove();
    arrivals.remove();
  };
}

// Manual log out: this phone stops getting this account's reminders. Best effort and not awaited, so a
// sleeping server never holds up log out; the request already carries the sign-in header when it starts.
export function stopReminders() {
  const token = registeredToken;
  registeredToken = null;
  if (token) request<void>('/users/me/push-token', { method: 'DELETE', body: { token } }).catch(() => {});
}
