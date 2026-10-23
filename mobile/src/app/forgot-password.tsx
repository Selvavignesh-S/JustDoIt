import { zodResolver } from '@hookform/resolvers/zod';
import { router } from 'expo-router';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { StyleSheet, View } from 'react-native';

import { requestPasswordReset } from '@/api/auth';
import { ApiError, isApiError } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { Screen } from '@/components/Screen';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { colors, space } from '@/theme/tokens';
import { ForgotForm, forgotSchema } from '@/utils/validators';

type Result = { kind: 'sent' } | { kind: 'unavailable'; message: string };

// S1a Forgot password (UI plan 7.3). While email is off (MAIL_PROVIDER=none) the server answers
// EMAIL_DISABLED for every address, so this shows "not available yet" instead of "check your email".
export default function ForgotPasswordScreen() {
  const [result, setResult] = useState<Result | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const { control, handleSubmit, formState } = useForm<ForgotForm>({ resolver: zodResolver(forgotSchema), defaultValues: { email: '' } });

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null);
    try {
      await requestPasswordReset(email.trim());
      setResult({ kind: 'sent' });
    } catch (e) {
      if (isApiError(e, 'EMAIL_DISABLED')) setResult({ kind: 'unavailable', message: e.message });
      else setFormError(e instanceof ApiError ? e.message : strings.errors.SERVER);
    }
  });

  if (result) {
    return (
      <Screen edges={['bottom']}>
        {result.kind === 'sent' ? (
          <Banner tone="info" title={strings.forgot.sentTitle} message={strings.forgot.sentBody} />
        ) : (
          <Banner tone="warning" title={strings.forgot.unavailableTitle} message={result.message} />
        )}
        <Button variant="secondary" label={strings.forgot.backToSignIn} onPress={() => router.back()} />
      </Screen>
    );
  }

  return (
    <Screen edges={['bottom']}>
      <AppText>{strings.forgot.intro}</AppText>
      <Controller
        control={control}
        name="email"
        render={({ field, fieldState }) => (
          <TextField
            label={strings.forgot.email}
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
        <Button label={strings.forgot.submit} onPress={onSubmit} loading={formState.isSubmitting} />
        {formError ? <AppText style={styles.error}>{formError}</AppText> : null}
      </View>
      <AppText variant="caption">{strings.forgot.hint}</AppText>
    </Screen>
  );
}

const styles = StyleSheet.create({
  actions: { gap: space.s },
  error: { color: colors.dueTomorrow, textAlign: 'center' },
});
