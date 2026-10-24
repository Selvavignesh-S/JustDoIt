import { Feather } from '@expo/vector-icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import * as DocumentPicker from 'expo-document-picker';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { downloadVersion, removeAttachment, UploadTarget } from '@/api/attachments';
import { isApiError } from '@/api/errors';
import { AttachmentSummary } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Sheet, SheetItem } from '@/components/Sheet';
import { useSnackbar } from '@/components/Snackbar';
import { UploadSheet } from '@/components/UploadSheet';
import { VersionsSheet } from '@/components/VersionsSheet';
import { strings } from '@/strings/en';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';
import { formatShortDate } from '@/utils/format';

// The "Files (n)" part of S7 with S8 (file menu), S9 (versions) and S10 (upload) (UI plan 7.9, 7.11).
// Files can be added to completed tasks too; the server decides (UI decision D19).
export function TaskFiles({ taskId, files }: { taskId: number; files: AttachmentSummary[] }) {
  const queryClient = useQueryClient();
  const showSnack = useSnackbar();
  const [menu, setMenu] = useState<AttachmentSummary | null>(null);
  const [versionsOf, setVersionsOf] = useState<AttachmentSummary | null>(null);
  const [removing, setRemoving] = useState<AttachmentSummary | null>(null);
  const [upload, setUpload] = useState<{ target: UploadTarget; asset: DocumentPicker.DocumentPickerAsset } | null>(null);
  const [conflict, setConflict] = useState(false);

  // The task's file list (and an open version list) are fetched again after every file change
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['task', taskId] });
    void queryClient.invalidateQueries({ queryKey: ['versions'] });
  };

  // The system picker opens first; the upload sheet only appears with a file in hand
  const pick = async (target: UploadTarget) => {
    const res = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true });
    if (!res.canceled) setUpload({ target, asset: res.assets[0] });
  };

  const remove = useMutation({
    mutationFn: (f: AttachmentSummary) => removeAttachment(f.id, f.version),
    onSuccess: () => {
      setRemoving(null);
      refresh();
    },
    onError: (e) => {
      setRemoving(null);
      if (isApiError(e, 'CONFLICT') || isApiError(e, 'NOT_AVAILABLE')) setConflict(true);
      else showSnack({ message: e instanceof Error ? e.message : strings.errors.SERVER });
    },
  });

  const download = (f: AttachmentSummary) => {
    if (f.currentVersionId === null) return;
    showSnack({ message: strings.files.downloading });
    downloadVersion(f.currentVersionId, f.displayName).catch(() => showSnack({ message: strings.files.downloadFailed }));
  };

  return (
    <View style={styles.block}>
      <View style={styles.header}>
        <AppText variant="section">{strings.detail.files(files.length)}</AppText>
        <Button variant="link" label={strings.files.addFile} onPress={() => pick({ kind: 'new', taskId })} />
      </View>
      {files.length ? files.map((f) => <FileRow key={f.id} file={f} onPress={() => setMenu(f)} />) : <AppText variant="caption">{strings.detail.noFiles}</AppText>}

      {/* S8. Each item closes the menu first, then opens what it leads to */}
      <Sheet visible={!!menu} onClose={() => setMenu(null)} title={menu?.displayName}>
        {menu ? (
          <>
            <SheetItem label={strings.files.viewVersions} onPress={() => (setVersionsOf(menu), setMenu(null))} />
            <SheetItem label={strings.files.downloadCurrent} onPress={() => (download(menu), setMenu(null))} />
            <SheetItem label={strings.files.uploadVersion} onPress={() => (setMenu(null), void pick({ kind: 'version', file: menu }))} />
            <SheetItem label={strings.files.removeFile} destructive onPress={() => (setRemoving(menu), setMenu(null))} />
          </>
        ) : null}
      </Sheet>

      {versionsOf ? (
        <VersionsSheet
          file={versionsOf}
          onClose={() => setVersionsOf(null)}
          onChanged={refresh}
          onConflict={() => (setVersionsOf(null), setConflict(true))}
          onUploadVersion={(f) => (setVersionsOf(null), void pick({ kind: 'version', file: f }))}
        />
      ) : null}

      {upload ? (
        <UploadSheet
          target={upload.target}
          asset={upload.asset}
          onClose={() => setUpload(null)}
          onConflict={() => (setUpload(null), setConflict(true))}
          onDone={(f) => {
            setUpload(null);
            refresh();
            showSnack({ message: strings.files.uploaded(f.currentVersionNumber ?? 1) });
          }}
        />
      ) : null}

      <ConfirmDialog
        visible={!!removing}
        title={removing ? strings.files.removeTitle(removing.displayName) : ''}
        body={strings.files.removeBody}
        confirmLabel={strings.files.removeFile}
        destructive
        loading={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing)}
        onCancel={() => setRemoving(null)}
      />
      <ConfirmDialog
        visible={conflict}
        title={strings.files.conflict}
        confirmLabel={strings.tasks.reload}
        onConfirm={() => {
          setConflict(false);
          refresh();
        }}
      />
    </View>
  );
}

// One file: type badge, name, "v3 · updated 18 Sep", ⋮. No file size: the API doesn't return one (UI decision D12).
// The whole row opens the file menu (one big target instead of a small ⋮ inside another button).
function FileRow({ file, onPress }: { file: AttachmentSummary; onPress: () => void }) {
  const ext = file.displayName.includes('.') ? file.displayName.split('.').pop()!.toUpperCase().slice(0, 4) : 'FILE';
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={strings.files.actionsFor(file.displayName)} style={({ pressed }) => [styles.file, pressed && styles.pressed]}>
      <View style={styles.badge}>
        <AppText style={styles.badgeText}>{ext}</AppText>
      </View>
      <View style={styles.fileBody}>
        <AppText numberOfLines={1}>{file.displayName}</AppText>
        <AppText variant="caption">{strings.detail.fileLine(file.currentVersionNumber, formatShortDate(file.updatedAt))}</AppText>
      </View>
      <Feather name="more-vertical" size={20} color={colors.muted} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  block: { gap: space.s },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  file: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.m,
    minHeight: minTouch,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: radius.card,
    padding: space.m,
  },
  pressed: { opacity: 0.6 },
  badge: { backgroundColor: colors.brandTint, borderRadius: radius.input, paddingHorizontal: space.s, paddingVertical: space.xs, minWidth: 44, alignItems: 'center' },
  badgeText: { color: colors.infoInk, fontFamily: fonts.bodyBold, fontSize: 12 },
  fileBody: { flex: 1, gap: 2 },
});
