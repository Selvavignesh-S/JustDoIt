import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { deleteAccount } from '@/api/auth';
import { isApiError } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Sheet } from '@/components/Sheet';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { colors, space } from '@/theme/tokens';

// S12 Delete account: the password is asked again so an unlocked phone can't wipe the account
export function DeleteAccountSheet({ onClose, onDeleted }: { onClose: () => void; onDeleted: () => void }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | undefined>();

  const remove = useMutation({
    mutationFn: () => deleteAccount(password),
    onSuccess: onDeleted,
    onError: (e) =>
      setError(isApiError(e, 'BAD_CREDENTIALS') ? strings.deleteAccount.wrongPassword : isApiError(e) ? e.message : strings.errors.SERVER),
  });

  const submit = () => {
    if (!password) return;
    setError(undefined);
    remove.mutate();
  };

  return (
    <Sheet visible onClose={onClose} title={strings.deleteAccount.title}>
      <AppText>{strings.deleteAccount.body}</AppText>
      <TextField
        label={strings.deleteAccount.password}
        value={password}
        onChangeText={setPassword}
        error={error}
        secret
        autoCapitalize="none"
        autoComplete="current-password"
        textContentType="password"
        onSubmitEditing={submit}
      />
      <View style={styles.actions}>
        <Button variant="destructive" label={strings.deleteAccount.confirm} onPress={submit} loading={remove.isPending} disabled={!password} style={styles.destructive} />
        <Button variant="secondary" label={strings.tasks.cancel} onPress={onClose} disabled={remove.isPending} />
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  actions: { gap: space.s, marginTop: space.s },
  destructive: { borderWidth: 1, borderColor: colors.dueTomorrow },
});
