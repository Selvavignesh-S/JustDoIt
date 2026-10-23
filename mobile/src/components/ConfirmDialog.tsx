import { Modal, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { colors, radius, space } from '@/theme/tokens';

type Props = {
  visible: boolean;
  title: string;
  body?: string;
  confirmLabel: string;
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  // Omit for a single-action dialog (e.g. the conflict "Reload")
  onCancel?: () => void;
  cancelLabel?: string;
};

// Centered dialog for confirmations and conflicts (UI plan 6)
export function ConfirmDialog({ visible, title, body, confirmLabel, destructive, loading, onConfirm, onCancel, cancelLabel = 'Cancel' }: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel ?? onConfirm}>
      <View style={styles.scrim}>
        <View style={styles.dialog} accessibilityViewIsModal accessibilityRole="alert">
          <AppText variant="section">{title}</AppText>
          {body ? <AppText>{body}</AppText> : null}
          <View style={styles.actions}>
            <Button
              label={confirmLabel}
              onPress={onConfirm}
              loading={loading}
              variant={destructive ? 'destructive' : 'primary'}
              style={destructive ? styles.destructive : undefined}
            />
            {onCancel ? <Button variant="secondary" label={cancelLabel} onPress={onCancel} disabled={loading} /> : null}
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  scrim: { flex: 1, backgroundColor: colors.scrim, justifyContent: 'center', padding: space.xl },
  dialog: { backgroundColor: colors.surface, borderRadius: radius.dialog, padding: space.xl, gap: space.m },
  actions: { gap: space.s, marginTop: space.s },
  destructive: { borderWidth: 1, borderColor: colors.dueTomorrow },
});
