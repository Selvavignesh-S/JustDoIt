import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, View } from 'react-native';

import { deleteVersion, downloadVersion, listVersions, restoreVersion } from '@/api/attachments';
import { isApiError } from '@/api/errors';
import { AttachmentSummary, AttachmentVersion } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { Chip } from '@/components/Chips';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Sheet } from '@/components/Sheet';
import { strings } from '@/strings/en';
import { colors, radius, space } from '@/theme/tokens';
import { formatDateTime, joinVersions } from '@/utils/format';

type Props = {
  file: AttachmentSummary;
  onClose: () => void;
  // with the attachment's latest row version, taken from this list
  onUploadVersion: (file: AttachmentSummary) => void;
  onChanged: () => void;
  onConflict: () => void;
};

// S9 (UI plan 7.11). Newest first. After every change the list is fetched again, so the next action
// always sends the newest attachment row version.
export function VersionsSheet({ file, onClose, onUploadVersion, onChanged, onConflict }: Props) {
  const query = useQuery({ queryKey: ['versions', file.id], queryFn: () => listVersions(file.id) });
  const [confirm, setConfirm] = useState<{ kind: 'restore' | 'delete'; v: AttachmentVersion } | null>(null);
  // Snackbars sit under this sheet, so its messages show inside it
  const [notice, setNotice] = useState<{ tone: 'info' | 'error'; message: string } | null>(null);

  const versions = query.data?.versions ?? [];
  const rowVersion = query.data?.attachmentVersion ?? file.version;

  const done = (message?: string) => {
    setConfirm(null);
    setNotice(message ? { tone: 'info', message } : null);
    void query.refetch();
    onChanged();
  };
  const failed = (e: unknown) => {
    setConfirm(null);
    if (isApiError(e, 'CONFLICT')) return onConflict();
    if (isApiError(e, 'NOT_AVAILABLE')) {
      setNotice({ tone: 'error', message: strings.files.versionGone });
      void query.refetch();
      return;
    }
    setNotice({ tone: 'error', message: e instanceof Error ? e.message : strings.errors.SERVER });
  };

  const restore = useMutation({
    mutationFn: (v: AttachmentVersion) => restoreVersion(v.id, rowVersion),
    onSuccess: (_, v) => done(strings.files.restored(v.versionNumber)),
    onError: failed,
  });
  const remove = useMutation({
    mutationFn: (v: AttachmentVersion) => deleteVersion(v.id, rowVersion),
    onSuccess: () => done(),
    onError: failed,
  });
  const download = useMutation({
    mutationFn: (v: AttachmentVersion) => downloadVersion(v.id, v.originalFileName),
    onError: () => setNotice({ tone: 'error', message: strings.files.downloadFailed }),
  });

  const only = versions.length === 1;
  const others = confirm ? versions.filter((v) => v.id !== confirm.v.id).map((v) => v.versionNumber) : [];

  return (
    <Sheet visible onClose={onClose} title={strings.files.versionsTitle} subtitle={file.displayName}>
      {notice ? <Banner tone={notice.tone} message={notice.message} /> : null}
      {query.isPending ? (
        <ActivityIndicator style={styles.loading} color={colors.brand} />
      ) : query.isError ? (
        <View style={styles.gap}>
          <Banner tone="error" message={query.error.message} />
          <Button variant="secondary" label={strings.common.retry} onPress={() => query.refetch()} />
        </View>
      ) : (
        <ScrollView style={styles.list} contentContainerStyle={styles.gap}>
          {versions.map((v) => (
            <View key={v.id} style={styles.row}>
              <View style={styles.head}>
                <AppText variant="bodyStrong">v{v.versionNumber}</AppText>
                {v.current ? <Chip label={strings.files.current} /> : null}
              </View>
              <AppText variant="caption">{strings.files.versionLine(formatDateTime(v.uploadedAt), v.originalFileName)}</AppText>
              {v.changeDescription ? <AppText>“{v.changeDescription}”</AppText> : null}
              <View style={styles.actions}>
                {v.current ? null : <Button variant="link" label={strings.files.restore} onPress={() => setConfirm({ kind: 'restore', v })} />}
                <Button variant="link" label={strings.files.download} loading={download.isPending && download.variables?.id === v.id} onPress={() => download.mutate(v)} />
                <Button variant="link" label={strings.files.delete} disabled={only} onPress={() => setConfirm({ kind: 'delete', v })} />
              </View>
              {only ? <AppText variant="caption">{strings.files.onlyVersion}</AppText> : null}
            </View>
          ))}
        </ScrollView>
      )}
      <Button variant="secondary" label={strings.files.uploadVersion} onPress={() => onUploadVersion({ ...file, version: rowVersion })} />

      {/* Inside the sheet, so the dialog stacks above it on iOS too */}
      <ConfirmDialog
        visible={!!confirm}
        title={confirm?.kind === 'restore' ? strings.files.restoreTitle(confirm.v.versionNumber) : confirm ? strings.files.deleteTitle(confirm.v.versionNumber) : ''}
        body={confirm?.kind === 'restore' ? strings.files.restoreBody(joinVersions(others), others.length > 1) : strings.files.deleteBody(!!confirm?.v.current)}
        confirmLabel={confirm?.kind === 'restore' ? strings.files.restore : strings.files.delete}
        destructive={confirm?.kind === 'delete'}
        loading={restore.isPending || remove.isPending}
        onConfirm={() => confirm && (confirm.kind === 'restore' ? restore : remove).mutate(confirm.v)}
        onCancel={() => setConfirm(null)}
      />
    </Sheet>
  );
}

const styles = StyleSheet.create({
  loading: { marginVertical: space.xl },
  list: { flexGrow: 0 },
  gap: { gap: space.s },
  row: { borderWidth: 1, borderColor: colors.line, borderRadius: radius.card, padding: space.m, gap: 2 },
  head: { flexDirection: 'row', alignItems: 'center', gap: space.s },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: space.l },
});
