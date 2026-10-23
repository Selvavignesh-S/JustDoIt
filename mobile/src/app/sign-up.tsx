import { zodResolver } from '@hookform/resolvers/zod';
import { router } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { StyleSheet, View } from 'react-native';

import { login, register } from '@/api/auth';
import { ApiError, isApiError } from '@/api/errors';
import { useSession } from '@/auth/session';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { colors, space } from '@/theme/tokens';
import { SignUpForm, signUpSchema } from '@/utils/validators';

const FIELDS = ['fullName', 'username', 'password', 'email'] as const;

// S2 Sign up (UI plan 7.2). Register returns no token, so the app logs in right after.
export default function SignUpScreen() {
  const { signIn } = useSession();
  const [formError, setFormError] = useState<string | null>(null);
  const { control, handleSubmit, setError, formState } = useForm<SignUpForm>({
    resolver: zodResolver(signUpSchema),
    defaultValues: { fullName: '', username: '', password: '', email: '' },
    mode: 'onBlur',
  });

  const onSubmit = handleSubmit(async (form) => {
    setFormError(null);
    try {
      await register({
        fullName: form.fullName.trim(),
        username: form.username.trim(),
        password: form.password,
        email: form.email.trim() || undefined,
      });
      await signIn(await login(form.username.trim(), form.password));
    } catch (e) {
      // "That username is taken." etc. come back per field; show them under the field they belong to
      if (isApiError(e, 'VALIDATION') && Object.keys(e.fieldErrors).length) {
        for (const [field, message] of Object.entries(e.fieldErrors)) {
          if ((FIELDS as readonly string[]).includes(field)) setError(field as (typeof FIELDS)[number], { message });
        }
        return;
      }
      setFormError(e instanceof ApiError ? e.message : strings.errors.SERVER);
    }
  });

  return (
    <Screen edges={['bottom']}>
      <Controller
        control={control}
        name="fullName"
        render={({ field, fieldState }) => (
          <TextField
            label={strings.signUp.fullName}
            autoComplete="name"
            textContentType="name"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={fieldState.error?.message}
          />
        )}
      />
      <Controller
        control={control}
        name="username"
        render={({ field, fieldState }) => (
          <TextField
            label={strings.signUp.username}
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="username-new"
            textContentType="username"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={fieldState.error?.message}
          />
        )}
      />
      <Controller
        control={control}
        name="password"
        render={({ field, fieldState }) => (
          <TextField
            label={strings.signUp.password}
            hint={strings.signUp.passwordHint}
            secret
            autoCapitalize="none"
            autoComplete="new-password"
            textContentType="newPassword"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={fieldState.error?.message}
          />
        )}
      />
      <Controller
        control={control}
        name="email"
        render={({ field, fieldState }) => (
          <TextField
            label={strings.signUp.email}
            hint={strings.signUp.emailHint}
            placeholder={strings.signUp.emailPlaceholder}
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            textContentType="emailAddress"
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={fieldState.error?.message}
          />
        )}
      />

      <View style={styles.actions}>
        <Button label={strings.signUp.submit} onPress={onSubmit} loading={formState.isSubmitting} />
        {formError ? (
          <AppText style={styles.error} accessibilityLiveRegion="polite">
            {formError}
          </AppText>
        ) : null}
      </View>

      <View style={styles.footer}>
        <AppText>{strings.signUp.haveAccount}</AppText>
        <Button variant="link" label={strings.signUp.signIn} onPress={() => router.back()} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  actions: { gap: space.s, marginTop: space.s },
  error: { color: colors.dueTomorrow, textAlign: 'center' },
  footer: { marginTop: 'auto', flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: space.xs },
});
