// The JWT is the only thing the app persists (UI plan rule 8).
// Phone: the OS keystore/keychain via expo-secure-store. Web preview: SecureStore isn't available there,
// so browser localStorage is used instead (development convenience only; the shipped app is native).
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const KEY = 'justdoit.jwt';

export async function readToken(): Promise<string | null> {
  if (Platform.OS === 'web') return globalThis.localStorage?.getItem(KEY) ?? null;
  return SecureStore.getItemAsync(KEY);
}

export async function saveToken(token: string): Promise<void> {
  if (Platform.OS === 'web') {
    globalThis.localStorage?.setItem(KEY, token);
    return;
  }
  await SecureStore.setItemAsync(KEY, token);
}

export async function deleteToken(): Promise<void> {
  if (Platform.OS === 'web') {
    globalThis.localStorage?.removeItem(KEY);
    return;
  }
  await SecureStore.deleteItemAsync(KEY);
}
