import { useQuery } from '@tanstack/react-query';
import { StyleSheet, View } from 'react-native';

import { getMe } from '@/api/auth';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { useSession } from '@/auth/session';
import { strings } from '@/strings/en';
import { colors, radius, space, type } from '@/theme/tokens';

// S12 Settings, first cut: account details and Log out. Email editing comes in Phase 5.
export default function SettingsScreen() {
  const { signOut } = useSession();
  const me = useQuery({ queryKey: ['me'], queryFn: getMe });

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
          <Row label={strings.settings.fullName} value={me.data?.fullName} />
          <Row label={strings.settings.username} value={me.data?.username} />
          <Row label={strings.settings.email} value={me.data ? (me.data.email ?? strings.settings.noEmail) : undefined} />
          {me.data?.email ? (
            <View style={styles.row}>
              <AppText variant="caption">{strings.settings.status}</AppText>
              <View style={[styles.chip, me.data.emailVerified ? styles.chipDone : styles.chipPending]}>
                <AppText style={[type.chip, { color: me.data.emailVerified ? colors.done : colors.medium }]}>
                  {me.data.emailVerified ? strings.settings.confirmed : strings.settings.notConfirmed}
                </AppText>
              </View>
            </View>
          ) : null}
        </View>
      )}

      {me.data?.email && !me.data.emailVerified ? <AppText variant="caption">{strings.settings.emailOff}</AppText> : null}

      {/* Log out asks nothing: token, cache and memory are cleared and the app returns to sign in (UI plan 7.12) */}
      <Button variant="destructive" label={strings.settings.logOut} onPress={() => signOut('manual')} style={styles.logout} />
      <AppText variant="caption" style={styles.version}>
        {strings.settings.version}
      </AppText>
    </Screen>
  );
}

function Row({ label, value }: { label: string; value?: string }) {
  return (
    <View style={styles.row}>
      <AppText variant="caption">{label}</AppText>
      <AppText style={styles.value}>{value ?? '…'}</AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  gap: { gap: space.s },
  card: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.line, borderRadius: radius.card, paddingHorizontal: space.l },
  row: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space.m, borderBottomWidth: 1, borderBottomColor: colors.line },
  value: { flexShrink: 1, textAlign: 'right' },
  chip: { borderRadius: radius.pill, paddingHorizontal: space.s, paddingVertical: 2 },
  chipDone: { backgroundColor: colors.doneTint },
  chipPending: { backgroundColor: colors.mediumTint },
  logout: { borderWidth: 1, borderColor: colors.dueTomorrow, marginTop: space.s },
  version: { textAlign: 'center' },
});
