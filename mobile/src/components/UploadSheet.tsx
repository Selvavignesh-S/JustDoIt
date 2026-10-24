import { DocumentPickerAsset } from 'expo-document-picker';
import { useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { uploadFile, UploadTarget } from '@/api/attachments';
import { isApiError } from '@/api/errors';
import { AttachmentSummary } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { Sheet } from '@/components/Sheet';
import { TextField } from '@/components/TextField';
import { strings } from '@/strings/en';
import { colors, radius, space } from '@/theme/tokens';
import { formatBytes } from '@/utils/format';
import { checkPickedFile } from '@/utils/validators';

type Props = {
  target: UploadTarget;
  asset: DocumentPickerAsset;
  onClose: () => void;
  onDone: (file: AttachmentSummary) => void;
  onConflict: () => void;
};

// S10 (UI plan 7.11): the file was already picked; this sheet checks it, takes the optional note,
// uploads with progress and Cancel, and offers Retry. It never retries by itself.
export function UploadSheet({ target, asset, onClose, onDone, onConflict }: Props) {
  const [note, setNote] = useState('');
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<{ message: string; retry: boolean } | null>(null);
  const abort = useRef<AbortController | null>(null);

  const check = checkPickedFile(asset.name, asset.size);
  const blocked = check === 'FILE_TYPE' ? strings.files.wrongType : check === 'FILE_TOO_LARGE' ? strings.files.tooLarge : null;
  const isVersion = target.kind === 'version';

  const start = async () => {
    abort.current = new AbortController();
    setError(null);
    setProgress(0);
    try {
      onDone(await uploadFile(target, asset, note, setProgress, abort.current.signal));
    } catch (e) {
      setProgress(null);
      if (isApiError(e, 'CANCELLED')) return;
      if (isApiError(e, 'CONFLICT')) return onConflict();
      // The server's own answer for a bad file or note; anything else means nothing was saved, so offer Retry
      const final = isApiError(e, 'FILE_TYPE') || isApiError(e, 'FILE_TOO_LARGE') || isApiError(e, 'VALIDATION') || isApiError(e, 'NOT_AVAILABLE');
      setError(final ? { message: (e as Error).message, retry: false } : { message: strings.files.saveFailed, retry: true });
    }
  };
  const close = () => {
    abort.current?.abort();
    onClose();
  };

  const uploading = progress !== null;
  return (
    <Sheet visible onClose={close} title={isVersion ? strings.files.uploadVersion : strings.files.addFile} subtitle={isVersion ? target.file.displayName : undefined}>
      <AppText numberOfLines={2}>{asset.size === undefined ? asset.name : `${asset.name} · ${formatBytes(asset.size)}`}</AppText>
      {blocked ? <Banner tone="error" message={blocked} /> : null}
      {isVersion && !blocked ? (
        <TextField label={strings.files.note} value={note} onChangeText={setNote} maxLength={500} editable={!uploading} multiline />
      ) : null}
      {uploading ? (
        <View style={styles.progressBlock} accessibilityRole="progressbar" accessibilityValue={{ min: 0, max: 100, now: Math.round(progress * 100) }}>
          <AppText variant="caption">{strings.files.uploading(Math.round(progress * 100))}</AppText>
          <View style={styles.track}>
            <View style={[styles.bar, { width: `${Math.round(progress * 100)}%` }]} />
          </View>
        </View>
      ) : null}
      {error ? <Banner tone="error" message={error.message} /> : null}

      <View style={styles.actions}>
        {uploading ? (
          <Button variant="secondary" label={strings.files.cancelUpload} onPress={close} />
        ) : (
          <>
            {blocked || (error && !error.retry) ? null : <Button label={error ? strings.common.retry : strings.files.upload} onPress={start} />}
            <Button variant="secondary" label={strings.tasks.cancel} onPress={close} />
          </>
        )}
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  progressBlock: { gap: space.xs },
  track: { height: 6, borderRadius: radius.pill, backgroundColor: colors.brandTint, overflow: 'hidden' },
  bar: { height: 6, backgroundColor: colors.brand },
  actions: { gap: space.s, marginTop: space.s },
});
