import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as WebBrowser from 'expo-web-browser';
import { ReactNode, useEffect, useState } from 'react';
import { AppState, Linking, StyleSheet, View } from 'react-native';

import { getMe, resendVerification } from '@/api/auth';
import { Me } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { DeleteAccountSheet } from '@/components/DeleteAccountSheet';
import { EmailSheet } from '@/components/EmailSheet';
import { Screen } from '@/components/Screen';
import { useSnackbar } from '@/components/Snackbar';
import { useSession } from '@/auth/session';
import { API_URL } from '@/config';
import { enableReminders, ReminderStatus } from '@/notifications/reminders';
import { strings } from '@/strings/en';
import { colors, radius, space, type } from '@/theme/tokens';

const RESEND_WAIT_S = 60;

// S12 Settings (UI plan 7.12): account and email, reminder notifications, privacy policy, Log out, Delete account.
export default function SettingsScreen() {
  const { signOut } = useSession();
  const queryClient = useQueryClient();
  const showSnack = useSnackbar();
  const me = useQuery({ queryKey: ['me'], queryFn: getMe });
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [wait, setWait] = useState(0);
  const [reminders, setReminders] = useState<ReminderStatus | null>(null);

  // Resend countdown, one second at a time
  useEffect(() => {
    if (wait <= 0) return;
    const t = setTimeout(() => setWait(wait - 1), 1000);
    return () => clearTimeout(t);
  }, [wait]);

  // Read the notification state now and again on return from system settings (never prompts here)
  useEffect(() => {
    void enableReminders(false).then(setReminders);
    const sub = AppState.addEventListener('change', (s) => s === 'active' && void enableReminders(false).then(setReminders));
    return () => sub.remove();
  }, []);

  const resend = useMutation({
    mutationFn: resendVerification,
    onSuccess: () => {
      setWait(RESEND_WAIT_S);
      showSnack({ message: strings.settings.linkSent });
    },
    onError: (e) => showSnack({ message: e instanceof Error ? e.message : strings.errors.SERVER }),
  });

  const turnOnReminders = async () => {
    const status = await enableReminders(true);
    setReminders(status);
    // Android asks only so often; after that the switch lives in system settings
    if (status === 'off') void Linking.openSettings();
  };

  const saved = (updated: Me) => {
    queryClient.setQueryData(['me'], updated);
    setEditing(false);
    if (updated.emailEnabled) setWait(RESEND_WAIT_S);
    showSnack({ message: updated.emailEnabled ? strings.settings.linkSent : strings.settings.emailSaved });
  };

  const deleted = () => {
    void signOut('manual');
    showSnack({ message: strings.deleteAccount.done });
  };

  const data = me.data;
  const unconfirmed = !!data?.email && !data.emailVerified;

  return (
    <Screen edges={['top']}>
      <AppText variant="heading">{strings.settings.title}</AppText>

      {me.isError ? (
        <View style={styles.gap}>
          <Banner tone="error" message={me.error.message} />
          <Button variant="secondary" label={strings.common.retry} onPress={() => me.refetch()} />
        </View>
      ) : (
        <View style={styles.card}>
          <Row label={strings.settings.fullName}>
            <Value text={data?.fullName} />
          </Row>
          <Row label={strings.settings.username}>
            <Value text={data?.username} />
          </Row>
          <Row label={strings.settings.email} last={!data?.email}>
            <View style={styles.end}>
              <Value text={data ? (data.email ?? strings.settings.noEmail) : undefined} />
              {data ? (
                <Button
                  variant="link"
                  label={data.email ? strings.settings.change : strings.settings.addEmail}
                  onPress={() => setEditing(true)}
                  style={styles.link}
                />
              ) : null}
            </View>
          </Row>
          {data?.email ? (
            <Row label={strings.settings.status} last>
              <View style={[styles.chip, data.emailVerified ? styles.chipDone : styles.chipPending]}>
                <AppText style={[type.chip, { color: data.emailVerified ? colors.done : colors.medium }]}>
                  {data.emailVerified ? strings.settings.confirmed : strings.settings.notConfirmed}
                </AppText>
              </View>
            </Row>
          ) : null}
        </View>
      )}

      {unconfirmed && data.emailEnabled ? (
        <View style={styles.hint}>
          <AppText variant="caption">{strings.settings.confirmHint}</AppText>
          <Button
            variant="link"
            label={wait > 0 ? strings.settings.resendIn(wait) : strings.settings.resend}
            onPress={() => resend.mutate()}
            loading={resend.isPending}
            disabled={wait > 0}
            style={styles.link}
          />
        </View>
      ) : unconfirmed ? (
        <AppText variant="caption">{strings.settings.emailOff}</AppText>
      ) : null}

      <View style={styles.card}>
        <Row label={strings.reminders.label} last>
          <View style={styles.end}>
            <Value
              text={reminders === null ? undefined : reminders === 'on' ? strings.reminders.on : reminders === 'off' ? strings.reminders.off : strings.reminders.unavailable}
            />
            {reminders === 'off' ? <Button variant="link" label={strings.reminders.turnOn} onPress={turnOnReminders} style={styles.link} /> : null}
          </View>
        </Row>
      </View>
      {reminders === 'off' ? <AppText variant="caption">{strings.reminders.offHint}</AppText> : null}
      {reminders === 'unavailable' ? <AppText variant="caption">{strings.reminders.unavailableHint}</AppText> : null}

      {/* Required for the Play Store (UI decision D17); the page is served by the backend */}
      <View style={styles.card}>
        <Button variant="link" label={strings.settings.privacy} onPress={() => void WebBrowser.openBrowserAsync(`${API_URL}/privacy.html`)} style={styles.privacy} />
      </View>

      {/* Log out asks nothing: token, cache and memory are cleared and the app returns to sign in (UI plan 7.12) */}
      <Button variant="destructive" label={strings.settings.logOut} onPress={() => signOut('manual')} style={styles.logout} />
      <Button variant="destructive" label={strings.settings.deleteAccount} onPress={() => setDeleting(true)} />
      <AppText variant="caption" style={styles.version}>
        {strings.settings.version}
      </AppText>

      {deleting ? <DeleteAccountSheet onClose={() => setDeleting(false)} onDeleted={deleted} /> : null}
      {editing && data ? <EmailSheet me={data} onClose={() => setEditing(false)} onSaved={saved} /> : null}
    </Screen>
  );
}

function Row({ label, children, last }: { label: string; children: ReactNode; last?: boolean }) {
  return (
    <View style={[styles.row, !last && styles.divider]}>
      <AppText variant="caption">{label}</AppText>
      {children}
    </View>
  );
}

function Value({ text }: { text?: string }) {
  return <AppText style={styles.value}>{text ?? '…'}</AppText>;
}

const styles = StyleSheet.create({
  gap: { gap: space.s },
  card: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderRadius: radius.card, paddingHorizontal: space.l },
  row: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.m, paddingVertical: space.s },
  divider: { borderBottomWidth: 1, borderBottomColor: colors.line },
  end: { flexShrink: 1, alignItems: 'flex-end' },
  value: { flexShrink: 1, textAlign: 'right' },
  link: { paddingHorizontal: 0 },
  hint: { gap: space.xs, alignItems: 'flex-start' },
  chip: { borderRadius: radius.pill, paddingHorizontal: space.s, paddingVertical: 2 },
  chipDone: { backgroundColor: colors.doneTint },
  chipPending: { backgroundColor: colors.mediumTint },
  privacy: { alignSelf: 'flex-start', paddingHorizontal: 0 },
  logout: { borderWidth: 1, borderColor: colors.dueTomorrow, marginTop: space.s },
  version: { textAlign: 'center' },
});
