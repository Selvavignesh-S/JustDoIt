import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { updateEmail } from '@/api/auth';
import { isApiError } from '@/api/errors';
import { Me } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Sheet } from '@/components/Sheet';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { space } from '@/theme/tokens';
import { forgotSchema } from '@/utils/validators';

// S12 email sheet (UI plan 7.12): Add or Change. There is no Remove: the route only replaces the address.
export function EmailSheet({ me, onClose, onSaved }: { me: Me; onClose: () => void; onSaved: (me: Me) => void }) {
  const [email, setEmail] = useState(me.email ?? '');
  const [error, setError] = useState<string | undefined>();

  const save = useMutation({
    mutationFn: (value: string) => updateEmail(value),
    onSuccess: onSaved,
    // e.g. "That email is already in use."
    onError: (e) => setError(isApiError(e) ? (e.fieldErrors.email ?? e.message) : strings.errors.SERVER),
  });

  const submit = () => {
    const parsed = forgotSchema.safeParse({ email: email.trim() });
    if (!parsed.success) return setError(parsed.error.issues[0]?.message);
    setError(undefined);
    save.mutate(parsed.data.email);
  };

  return (
    <Sheet visible onClose={onClose} title={me.email ? strings.settings.changeTitle : strings.settings.addTitle}>
      <TextField
        label={strings.settings.email}
        value={email}
        onChangeText={setEmail}
        error={error}
        keyboardType="email-address"
        autoCapitalize="none"
        autoComplete="email"
        maxLength={255}
        onSubmitEditing={submit}
      />
      <AppText variant="caption">{me.emailEnabled ? strings.settings.sheetHint : strings.settings.sheetHintOff}</AppText>
      <View style={styles.actions}>
        <Button label={me.emailEnabled ? strings.settings.send : strings.settings.save} onPress={submit} loading={save.isPending} disabled={!email.trim()} />
        <Button variant="secondary" label={strings.tasks.cancel} onPress={onClose} disabled={save.isPending} />
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  actions: { gap: space.s, marginTop: space.s },
});
