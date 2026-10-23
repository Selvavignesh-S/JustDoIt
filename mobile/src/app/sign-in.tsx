import { zodResolver } from '@hookform/resolvers/zod';
import { router } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { StyleSheet, View } from 'react-native';

import { login } from '@/api/auth';
import { ApiError, isApiError } from '@/api/errors';
import { useSession } from '@/auth/session';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { colors, space } from '@/theme/tokens';
import { SignInForm, signInSchema } from '@/utils/validators';

// S1 Sign in (UI plan 7.1)
export default function SignInScreen() {
  const { signIn, signedOutReason } = useSession();
  const [formError, setFormError] = useState<string | null>(null);
  const { control, handleSubmit, formState } = useForm<SignInForm>({
    resolver: zodResolver(signInSchema),
    defaultValues: { username: '', password: '' },
  });
  const [username, password] = useWatch({ control, name: ['username', 'password'] });

  const onSubmit = handleSubmit(async (form) => {
    setFormError(null);
    try {
      await signIn(await login(form.username.trim(), form.password));
      // The navigator's guard switches to the app tabs on its own
    } catch (e) {
      // Wrong username and wrong password get the same message, never which one (UI plan 7.1)
      setFormError(isApiError(e, 'BAD_CREDENTIALS') ? strings.signIn.wrongCredentials : e instanceof ApiError ? e.message : strings.errors.SERVER);
    }
  });

  return (
    <Screen>
      <View style={styles.header}>
        <AppText variant="title">{strings.appName}</AppText>
        <AppText style={styles.subtitle}>{strings.signIn.subtitle}</AppText>
      </View>

      {signedOutReason === 'expired' ? <Banner tone="warning" message={strings.signIn.signedOut} /> : null}

      <Controller
        control={control}
        name="username"
        render={({ field }) => (
          <TextField
            label={strings.signIn.username}
            placeholder={strings.signIn.usernamePlaceholder}
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="username"
            textContentType="username"
            returnKeyType="next"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
          />
        )}
      />
      <Controller
        control={control}
        name="password"
        render={({ field }) => (
          <TextField
            label={strings.signIn.password}
            secret
            autoCapitalize="none"
            autoComplete="current-password"
            textContentType="password"
            returnKeyType="go"
            onSubmitEditing={() => onSubmit()}
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
          />
        )}
      />

      <View style={styles.actions}>
        {/* Disabled until both fields have text (UI plan 7.1) */}
        <Button label={strings.signIn.submit} onPress={onSubmit} loading={formState.isSubmitting} disabled={!username.trim() || !password} />
        {formError ? (
          <AppText style={styles.error} accessibilityLiveRegion="polite">
            {formError}
          </AppText>
        ) : null}
        <Button variant="link" label={strings.signIn.forgot} onPress={() => router.push('/forgot-password')} />
      </View>

      <View style={styles.footer}>
        <AppText>{strings.signIn.noAccount}</AppText>
        <Button variant="link" label={strings.signIn.signUp} onPress={() => router.push('/sign-up')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { marginTop: space.xxl, gap: space.xs },
  subtitle: { color: colors.muted },
  actions: { gap: space.s },
  error: { color: colors.dueTomorrow, textAlign: 'center' },
  footer: { marginTop: 'auto', flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: space.xs },
});
