// File and version routes (Route plan sections 5 and 6). "version" is always the ATTACHMENT's row version.
import { DocumentPickerAsset } from 'expo-document-picker';
import { Directory, File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { Platform } from 'react-native';

import { API_URL } from '@/config';
import { strings } from '@/strings/en';

import { authHeaders, request, upload } from './client';
import { ApiError } from './errors';
import { AttachmentSummary, VersionList } from './types';

export const listVersions = (id: number) => request<VersionList>(`/attachments/${id}/versions`);
export const removeAttachment = (id: number, version: number) =>
  request<void>(`/attachments/${id}?version=${version}`, { method: 'DELETE' });
export const restoreVersion = (versionId: number, version: number) =>
  request<AttachmentSummary>(`/versions/${versionId}/restore`, { method: 'POST', body: { version } });
export const deleteVersion = (versionId: number, version: number) =>
  request<AttachmentSummary>(`/versions/${versionId}?version=${version}`, { method: 'DELETE' });

// A new file on a task, or a new version of an existing file (UI plan 7.11, S10)
export type UploadTarget = { kind: 'new'; taskId: number } | { kind: 'version'; file: AttachmentSummary };

export function uploadFile(target: UploadTarget, asset: DocumentPickerAsset, note: string, onProgress: (f: number) => void, signal: AbortSignal) {
  const form = new FormData();
  // Web hands us a real File; native sends the picked file by its uri (React Native's FormData shape)
  form.append('file', asset.file ?? ({ uri: asset.uri, name: asset.name, type: asset.mimeType ?? 'application/octet-stream' } as unknown as Blob));
  if (target.kind === 'new') return upload<AttachmentSummary>(`/tasks/${target.taskId}/attachments`, form, onProgress, signal);
  form.append('version', String(target.file.version));
  if (note.trim()) form.append('changeDescription', note.trim());
  return upload<AttachmentSummary>(`/attachments/${target.file.id}/versions`, form, onProgress, signal);
}

// Saves one version to the cache, then opens the system share / open-with sheet (UI plan 7.11, S8).
// On the web the browser downloads it instead.
export async function downloadVersion(versionId: number, fileName: string) {
  const url = `${API_URL}/versions/${versionId}/download`;
  const safeName = fileName.replace(/[\\/]/g, '_');
  try {
    if (Platform.OS === 'web') {
      const res = await fetch(url, { headers: authHeaders() });
      if (!res.ok) throw new Error(String(res.status));
      const href = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = href;
      a.download = safeName;
      a.click();
      URL.revokeObjectURL(href);
      return;
    }
    // One folder per version, so two versions with the same name never overwrite each other
    const dir = new Directory(Paths.cache, 'downloads', String(versionId));
    dir.create({ intermediates: true, idempotent: true });
    const file = await File.downloadFileAsync(url, new File(dir, safeName), { headers: authHeaders(), idempotent: true });
    await Sharing.shareAsync(file.uri);
  } catch {
    throw new ApiError('SERVER', strings.files.downloadFailed);
  }
}
