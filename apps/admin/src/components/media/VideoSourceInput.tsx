import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Download, Eye, EyeOff, FileVideo, Link2, Loader2, RefreshCw, Replace, RotateCcw, Trash2, UploadCloud, X } from 'lucide-react';
import { adminService, audit } from '../../services/adminService';
import { errorMessage } from '../../api/client';
import type { MediaAsset } from '../../types';
import { chunkedUpload, fileExtension, isUploadCancelled } from '../../utils/chunkedUpload';
import type { UploadProgress } from '../../utils/chunkedUpload';
import { formatBytes, formatDateTime, formatDuration } from '../../utils/format';
import { isHttpUrl, resolveApiUrl } from '../../utils/media';
import { useUnloadGuard } from '../../hooks/useUnloadGuard';
import { StatusBadge } from '../common/StatusBadge';
import { MEDIA_POLL_INTERVAL_MS, ProgressBar, UploadProgressBar, assetStatusLabel, isAssetActive } from '../common/VideoUpload';
import { HlsPlayer } from './HlsPlayer';

/** A video chosen before its movie exists; uploaded/imported right after the movie is created. */
export type PendingVideo = { kind: 'file'; file: File } | { kind: 'link'; url: string };

const VIDEO_EXTENSIONS = ['mp4', 'mov', 'mkv', 'webm', 'avi', 'm4v', 'mpg', 'mpeg', 'ts'];
export const VIDEO_ACCEPT = `video/*,${VIDEO_EXTENSIONS.map((ext) => `.${ext}`).join(',')}`;

export function videoFileError(file: File): string | null {
  if (file.type.startsWith('video/') || VIDEO_EXTENSIONS.includes(fileExtension(file.name))) return null;
  return `"${file.name}" is not a supported video (${VIDEO_EXTENSIONS.join(', ')}).`;
}

export function videoLinkError(url: string): string | null {
  if (!url.trim()) return 'Paste a link to the video file.';
  return isHttpUrl(url) ? null : 'The link must be an http(s) URL.';
}

const BUTTON_SECONDARY =
  'flex items-center gap-1.5 px-2.5 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-800 text-[11px] font-semibold transition disabled:opacity-50';
const BUTTON_DANGER =
  'flex items-center gap-1.5 px-2.5 py-1 rounded border border-rose-500/30 text-rose-300 hover:bg-rose-500/10 text-[11px] font-semibold transition disabled:opacity-50';
const TAB_CLASS = (active: boolean) =>
  `flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold transition ${
    active ? 'bg-red-600 text-white' : 'bg-slate-950 border border-slate-800 text-slate-400 hover:text-white'
  }`;
const INPUT_CLASS =
  'w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500 disabled:opacity-50';

/** File-or-link picker used before the playable item exists (e.g. on the create movie form). */
export const PendingVideoPicker: React.FC<{
  value: PendingVideo | null;
  onChange: (value: PendingVideo | null) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => {
  const [mode, setMode] = useState<'file' | 'link'>(value?.kind ?? 'file');
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const switchMode = (next: 'file' | 'link') => {
    setMode(next);
    setError(null);
    if (value && value.kind !== next) onChange(null);
  };

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const invalid = videoFileError(file);
    setError(invalid);
    onChange(invalid ? null : { kind: 'file', file });
  };

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <button type="button" disabled={disabled} onClick={() => switchMode('file')} className={TAB_CLASS(mode === 'file')}>
          <UploadCloud className="w-3.5 h-3.5" />
          Upload video
        </button>
        <button type="button" disabled={disabled} onClick={() => switchMode('link')} className={TAB_CLASS(mode === 'link')}>
          <Link2 className="w-3.5 h-3.5" />
          Import from link
        </button>
      </div>
      {mode === 'file' ? (
        <div className="flex flex-wrap items-center gap-2">
          <input ref={fileInputRef} type="file" accept={VIDEO_ACCEPT} className="hidden" onChange={handleFile} />
          <button type="button" disabled={disabled} onClick={() => fileInputRef.current?.click()} className={BUTTON_SECONDARY}>
            <FileVideo className="w-3 h-3" />
            {value?.kind === 'file' ? 'Choose a different file' : 'Choose video file'}
          </button>
          {value?.kind === 'file' && (
            <>
              <span className="text-[11px] text-slate-300 truncate max-w-xs">
                {value.file.name} · {formatBytes(value.file.size)}
              </span>
              <button type="button" disabled={disabled} onClick={() => onChange(null)} className={BUTTON_DANGER}>
                <X className="w-3 h-3" />
                Clear
              </button>
            </>
          )}
        </div>
      ) : (
        <input
          type="url"
          disabled={disabled}
          placeholder="https://example.com/movie.mp4"
          value={value?.kind === 'link' ? value.url : ''}
          onChange={(e) => onChange(e.target.value.trim() ? { kind: 'link', url: e.target.value } : null)}
          className={INPUT_CLASS}
        />
      )}
      {error && <p className="text-[11px] text-rose-300">{error}</p>}
      <p className="text-[10px] text-slate-500">
        {mode === 'file'
          ? 'Any size: the file is sent in 32 MB chunks after the movie is created, then transcoded to HLS.'
          : 'Must link directly to a video file (e.g. .mp4). Web pages such as YouTube cannot be imported.'}
      </p>
    </div>
  );
};

interface VideoSourceInputProps {
  /** The playable item: a movie id or an episode id. */
  contentId: string;
  /** Human description used in messages and the audit trail, e.g. `Movie "Dune"` or `Show · S1E2 · Pilot`. */
  contentLabel: string;
  auditTargetType: 'MOVIE' | 'EPISODE';
  /** Known asset (null = no video). Leave undefined to let the component fetch it. */
  asset?: MediaAsset | null;
  /** Poll the asset every 5 s while it is uploading/processing. Disable when the parent already polls. */
  selfPoll?: boolean;
  onAssetChange?: (asset: MediaAsset | null) => void;
  onUploadChange?: (progress: UploadProgress | null) => void;
  /** Started automatically once on mount (used right after creating a movie). */
  autoStart?: PendingVideo | null;
}

export const VideoSourceInput: React.FC<VideoSourceInputProps> = ({
  contentId,
  contentLabel,
  auditTargetType,
  asset: assetProp,
  selfPoll = true,
  onAssetChange,
  onUploadChange,
  autoStart,
}) => {
  const [asset, setAsset] = useState<MediaAsset | null | undefined>(assetProp);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [replacing, setReplacing] = useState(false);
  const [mode, setMode] = useState<'upload' | 'link'>('upload');
  const [linkUrl, setLinkUrl] = useState('');
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  const [failedFile, setFailedFile] = useState<File | null>(null);
  const [busy, setBusy] = useState<'import' | 'retry' | 'delete' | 'preview' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const controllerRef = useRef<AbortController | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const autoStartedRef = useRef(false);
  const onAssetChangeRef = useRef(onAssetChange);
  const onUploadChangeRef = useRef(onUploadChange);
  onAssetChangeRef.current = onAssetChange;
  onUploadChangeRef.current = onUploadChange;

  const uploading = progress !== null;
  useUnloadGuard(uploading);

  useEffect(() => {
    if (assetProp !== undefined) setAsset(assetProp);
  }, [assetProp]);

  useEffect(() => {
    onUploadChangeRef.current?.(progress);
  }, [progress]);

  useEffect(() => () => controllerRef.current?.abort(), []);

  const updateAsset = useCallback((next: MediaAsset | null) => {
    setAsset(next);
    onAssetChangeRef.current?.(next);
  }, []);

  const load = useCallback(
    async (silent: boolean) => {
      try {
        const latest = await adminService.getAssetByContent(contentId);
        updateAsset(latest);
        setLoadError(null);
      } catch (err) {
        setLoadError(errorMessage(err, silent ? 'Could not refresh the video status' : 'Failed to load the video status'));
      }
    },
    [contentId, updateAsset]
  );

  useEffect(() => {
    // Only on mount / when the content changes: later prop updates are synced by the effect above.
    if (assetProp === undefined) void load(false);
  }, [contentId]);

  const active = asset ? isAssetActive(asset) : false;
  useEffect(() => {
    if (!selfPoll || !active) return undefined;
    const timer = window.setInterval(() => void load(true), MEDIA_POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [selfPoll, active, load]);

  const clearMessages = () => {
    setError(null);
    setNotice(null);
  };

  const startUpload = useCallback(
    async (file: File) => {
      const invalid = videoFileError(file);
      if (invalid) {
        setError(invalid);
        return;
      }
      const controller = new AbortController();
      controllerRef.current = controller;
      setError(null);
      setNotice(null);
      setFailedFile(null);
      setPreviewUrl(null);
      setProgress({ phase: 'preparing', uploadedBytes: 0, totalBytes: file.size, percent: 0, partsDone: 0, totalParts: 0 });
      try {
        const result = await chunkedUpload({
          file,
          purpose: 'VIDEO',
          contentId,
          signal: controller.signal,
          onProgress: setProgress,
        });
        if (result.asset) updateAsset(result.asset);
        else void load(true);
        setReplacing(false);
        audit({ action: 'MEDIA_UPLOADED', targetType: auditTargetType, targetId: contentId, details: `${contentLabel} · ${file.name}` });
        setNotice(`"${file.name}" uploaded. Transcoding to HLS runs in the background.`);
      } catch (err) {
        if (isUploadCancelled(err) || controller.signal.aborted) {
          setNotice('Upload cancelled.');
        } else {
          setError(errorMessage(err, 'Upload failed'));
          setFailedFile(file);
        }
      } finally {
        if (controllerRef.current === controller) controllerRef.current = null;
        setProgress(null);
      }
    },
    [auditTargetType, contentId, contentLabel, load, updateAsset]
  );

  const startImport = useCallback(
    async (url: string) => {
      const invalid = videoLinkError(url);
      if (invalid) {
        setError(invalid);
        return;
      }
      setBusy('import');
      setError(null);
      setNotice(null);
      setPreviewUrl(null);
      try {
        const imported = await adminService.importVideo(contentId, url.trim());
        updateAsset(imported);
        setReplacing(false);
        setLinkUrl('');
        audit({ action: 'MEDIA_IMPORT_STARTED', targetType: auditTargetType, targetId: contentId, details: `${contentLabel} · ${url.trim()}` });
        setNotice('Import started: the server is downloading the video, then it will be transcoded.');
      } catch (err) {
        setError(errorMessage(err, 'Failed to start the import'));
      } finally {
        setBusy(null);
      }
    },
    [auditTargetType, contentId, contentLabel, updateAsset]
  );

  useEffect(() => {
    if (!autoStart) return undefined;
    // Deferred so React StrictMode's mount/unmount/mount cycle starts the transfer only once.
    const timer = window.setTimeout(() => {
      if (autoStartedRef.current) return;
      autoStartedRef.current = true;
      if (autoStart.kind === 'file') void startUpload(autoStart.file);
      else void startImport(autoStart.url);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (file) void startUpload(file);
  };

  const retryTranscode = async () => {
    if (!asset) return;
    setBusy('retry');
    clearMessages();
    try {
      const updated = await adminService.retryTranscode(asset.id);
      updateAsset(updated);
      audit({ action: 'MEDIA_TRANSCODE_RETRIED', targetType: 'MEDIA', targetId: asset.id, details: `${contentLabel} · ${asset.originalFilename ?? contentId}` });
      setNotice(`Transcode re-queued (status: ${updated.status}).`);
    } catch (err) {
      setError(errorMessage(err, 'Failed to retry the transcode'));
    } finally {
      setBusy(null);
    }
  };

  const deleteVideo = async () => {
    if (!asset) return;
    if (!window.confirm(`Delete the video of ${contentLabel}? Viewers will no longer be able to play it. This cannot be undone.`)) return;
    setBusy('delete');
    clearMessages();
    try {
      await adminService.deleteMediaAsset(asset.id);
      updateAsset(null);
      setPreviewUrl(null);
      setReplacing(false);
      audit({ action: 'MEDIA_ASSET_DELETED', targetType: auditTargetType, targetId: contentId, details: `${contentLabel} · ${asset.originalFilename ?? asset.id}` });
      setNotice('Video deleted.');
    } catch (err) {
      setError(errorMessage(err, 'Failed to delete the video'));
    } finally {
      setBusy(null);
    }
  };

  const togglePreview = async () => {
    if (previewUrl) {
      setPreviewUrl(null);
      return;
    }
    setBusy('preview');
    clearMessages();
    try {
      const { streamUrl } = await adminService.getAssetPreview(contentId);
      setPreviewUrl(resolveApiUrl(streamUrl));
    } catch (err) {
      setError(errorMessage(err, 'Failed to open the preview'));
    } finally {
      setBusy(null);
    }
  };

  const showPicker = !uploading && (asset === null || replacing);
  const statusLabel = asset ? assetStatusLabel(asset) : null;

  return (
    <div className="space-y-3">
      {asset === undefined && !loadError && (
        <p className="text-[11px] text-slate-500 flex items-center gap-1.5">
          <Loader2 className="w-3.5 h-3.5 animate-spin text-red-500" />
          Checking video status…
        </p>
      )}
      {loadError && (
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-[11px] text-rose-300">{loadError}</p>
          <button type="button" onClick={() => void load(false)} className={BUTTON_SECONDARY}>
            <RefreshCw className="w-3 h-3" />
            Retry
          </button>
        </div>
      )}

      {asset && (
        <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={asset.status} />
            {statusLabel && <span className="text-[11px] text-amber-300">{statusLabel}</span>}
            {asset.status === 'COMPLETED' && (
              <span className="text-[11px] text-slate-400">
                {formatDuration(asset.durationSeconds)} · {formatBytes(asset.fileSizeBytes)} source
              </span>
            )}
          </div>
          {asset.status === 'PROCESSING' && asset.progressPercent != null && (
            <ProgressBar percent={asset.progressPercent} compact tone="amber" />
          )}
          <p className="text-[11px] text-slate-500 break-all">
            {asset.sourceUrl ? (
              <>
                <Download className="w-3 h-3 inline mr-1" />
                Imported from {asset.sourceUrl}
              </>
            ) : (
              <>
                <FileVideo className="w-3 h-3 inline mr-1" />
                {asset.originalFilename ?? 'Uploaded file'}
              </>
            )}
            <span className="text-slate-600"> · updated {formatDateTime(asset.updatedAt ?? asset.createdAt)}</span>
          </p>
          {asset.status === 'FAILED' && asset.failureReason && (
            <pre className="text-[10px] text-rose-300 bg-rose-500/5 border border-rose-500/20 rounded p-2 whitespace-pre-wrap break-words max-h-40 overflow-y-auto">
              {asset.failureReason}
            </pre>
          )}
          {active && selfPoll && <p className="text-[10px] text-slate-500">Status refreshes every 5 seconds.</p>}

          {!uploading && (
            <div className="flex flex-wrap gap-2 pt-1">
              {asset.status === 'COMPLETED' && (
                <button type="button" onClick={() => void togglePreview()} disabled={busy !== null} className={BUTTON_SECONDARY}>
                  {previewUrl ? <EyeOff className="w-3 h-3" /> : <Eye className="w-3 h-3" />}
                  {busy === 'preview' ? 'Opening…' : previewUrl ? 'Hide preview' : 'Preview'}
                </button>
              )}
              {asset.status === 'FAILED' && (
                <button type="button" onClick={() => void retryTranscode()} disabled={busy !== null} className={BUTTON_SECONDARY}>
                  <RotateCcw className={`w-3 h-3 ${busy === 'retry' ? 'animate-spin' : ''}`} />
                  {busy === 'retry' ? 'Retrying…' : 'Retry transcode'}
                </button>
              )}
              {!active && !replacing && (
                <button
                  type="button"
                  onClick={() => {
                    clearMessages();
                    setReplacing(true);
                  }}
                  disabled={busy !== null}
                  className={BUTTON_SECONDARY}
                >
                  <Replace className="w-3 h-3" />
                  Replace video
                </button>
              )}
              {asset.status !== 'PROCESSING' && (
                <button type="button" onClick={() => void deleteVideo()} disabled={busy !== null} className={BUTTON_DANGER}>
                  <Trash2 className="w-3 h-3" />
                  {busy === 'delete' ? 'Deleting…' : 'Delete video'}
                </button>
              )}
            </div>
          )}
          {previewUrl && <HlsPlayer src={previewUrl} className="w-full max-w-xl rounded-lg bg-black aspect-video" />}
        </div>
      )}

      {uploading && progress && (
        <div className="bg-slate-950 border border-slate-800 rounded-lg p-3 space-y-2">
          <UploadProgressBar progress={progress} />
          <div className="flex items-center justify-between gap-2">
            <p className="text-[10px] text-slate-500">Keep this page open until the upload finishes.</p>
            <button type="button" onClick={() => controllerRef.current?.abort()} className={BUTTON_DANGER}>
              <X className="w-3 h-3" />
              Cancel upload
            </button>
          </div>
        </div>
      )}

      {showPicker && (
        <div className="space-y-2">
          {asset === null && <p className="text-[11px] text-slate-500">No video yet. Viewers can't play this until one is uploaded and transcoded.</p>}
          {replacing && (
            <p className="text-[11px] text-slate-400">The current video keeps playing until the new one finishes transcoding.</p>
          )}
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={busy !== null} onClick={() => setMode('upload')} className={TAB_CLASS(mode === 'upload')}>
              <UploadCloud className="w-3.5 h-3.5" />
              Upload video
            </button>
            <button type="button" disabled={busy !== null} onClick={() => setMode('link')} className={TAB_CLASS(mode === 'link')}>
              <Link2 className="w-3.5 h-3.5" />
              Import from link
            </button>
            {replacing && (
              <button type="button" onClick={() => setReplacing(false)} disabled={busy !== null} className={BUTTON_SECONDARY}>
                <X className="w-3 h-3" />
                Keep current video
              </button>
            )}
          </div>
          {mode === 'upload' ? (
            <div className="flex flex-wrap items-center gap-2">
              <input ref={fileInputRef} type="file" accept={VIDEO_ACCEPT} className="hidden" onChange={handleFile} />
              <button type="button" disabled={busy !== null} onClick={() => fileInputRef.current?.click()} className={BUTTON_SECONDARY}>
                <FileVideo className="w-3 h-3" />
                Choose video file
              </button>
              <span className="text-[10px] text-slate-500">Sent in 32 MB chunks; an interrupted upload of the same file resumes.</span>
            </div>
          ) : (
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                type="url"
                value={linkUrl}
                disabled={busy !== null}
                placeholder="https://example.com/episode.mp4"
                onChange={(e) => setLinkUrl(e.target.value)}
                className={INPUT_CLASS}
              />
              <button
                type="button"
                disabled={busy !== null || !linkUrl.trim()}
                onClick={() => void startImport(linkUrl)}
                className="flex items-center justify-center gap-1.5 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50 whitespace-nowrap"
              >
                <Download className="w-3.5 h-3.5" />
                {busy === 'import' ? 'Starting…' : 'Import'}
              </button>
            </div>
          )}
        </div>
      )}

      {error && (
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-[11px] text-rose-300 break-words">{error}</p>
          {failedFile && !uploading && (
            <button type="button" onClick={() => void startUpload(failedFile)} className={BUTTON_SECONDARY}>
              <RotateCcw className="w-3 h-3" />
              Resume upload
            </button>
          )}
        </div>
      )}
      {notice && <p className="text-[11px] text-emerald-300">{notice}</p>}
    </div>
  );
};
