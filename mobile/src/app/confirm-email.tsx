import { useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, StyleSheet } from 'react-native';

import { verifyEmail } from '@/api/auth';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { useSession } from '@/auth/session';
import { strings } from '@/strings/en';
import { colors, space } from '@/theme/tokens';

// Opened by justdoit://confirm-email?code=… from the email, signed in or out (UI plan 5.2, 7.12).
// A query, not a mutation, so the one-time code is sent once even if the screen renders twice.
export default function ConfirmEmailScreen() {
  const { code } = useLocalSearchParams<{ code?: string }>();
  const { token } = useSession();
  const queryClient = useQueryClient();
  const confirm = useQuery({
    queryKey: ['verify-email', code],
    queryFn: () => verifyEmail(code ?? '').then(() => true),
    enabled: !!code,
    staleTime: Infinity,
  });

  const leave = () => {
    // Settings shows "Confirmed" straight away
    void queryClient.invalidateQueries({ queryKey: ['me'] });
    router.replace(token ? '/settings' : '/sign-in');
  };

  return (
    <Screen>
      {confirm.isLoading ? (
        <>
          <ActivityIndicator style={styles.loading} color={colors.brand} />
          <AppText style={styles.center}>{strings.confirmEmail.checking}</AppText>
        </>
      ) : (
        <>
          {/* A missing, used or expired code all read the same */}
          <Banner tone={confirm.isSuccess ? 'info' : 'warning'} message={confirm.isSuccess ? strings.confirmEmail.done : strings.confirmEmail.expired} />
          <Button label={token ? strings.confirmEmail.toSettings : strings.confirmEmail.toSignIn} onPress={leave} />
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  loading: { marginTop: space.xxl },
  center: { textAlign: 'center' },
});
