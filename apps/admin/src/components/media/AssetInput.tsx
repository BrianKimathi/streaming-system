import React, { useEffect, useRef, useState } from 'react';
import { Film, Image as ImageIcon, Link2, RotateCcw, Trash2, UploadCloud, X } from 'lucide-react';
import { errorMessage } from '../../api/client';
import { chunkedUpload, fileExtension, isUploadCancelled } from '../../utils/chunkedUpload';
import type { UploadProgress } from '../../utils/chunkedUpload';
import { isHttpUrl, isMediaServiceUrl, linkError } from '../../utils/media';
import { useUnloadGuard } from '../../hooks/useUnloadGuard';
import { UploadProgressBar } from '../common/VideoUpload';

export type AssetKind = 'image' | 'trailer';
export type AssetPreviewShape = 'poster' | 'backdrop' | 'thumbnail';

const ACCEPT: Record<AssetKind, string> = {
  image: 'image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp',
  trailer: 'video/mp4,video/webm,video/quicktime,.mp4,.webm,.mov,.m4v',
};

const EXTENSIONS: Record<AssetKind, string[]> = {
  image: ['jpg', 'jpeg', 'png', 'webp'],
  trailer: ['mp4', 'webm', 'mov', 'm4v'],
};

const PREVIEW_CLASS: Record<AssetPreviewShape, string> = {
  poster: 'w-16 h-24',
  backdrop: 'w-36 aspect-video',
  thumbnail: 'w-32 aspect-video',
};

const TAB_CLASS = (active: boolean) =>
  `flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold transition ${
    active ? 'bg-slate-700 text-white' : 'text-slate-400 hover:text-white'
  }`;

const BUTTON_SECONDARY =
  'flex items-center gap-1.5 px-2.5 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-800 text-[11px] font-semibold transition disabled:opacity-50';

function typeError(kind: AssetKind, file: File): string | null {
  const ext = fileExtension(file.name);
  const allowedType = kind === 'image' ? /^image\/(jpeg|png|webp)$/ : /^video\/(mp4|webm|quicktime|x-m4v)$/;
  if (EXTENSIONS[kind].includes(ext) || allowedType.test(file.type)) return null;
  return kind === 'image'
    ? `"${file.name}" is not a JPEG, PNG or WebP image.`
    : `"${file.name}" is not an MP4, WebM or MOV video (trailers are played as-is, so they must be browser-playable).`;
}

interface AssetInputProps {
  label: string;
  kind: AssetKind;
  value: string;
  onChange: (url: string) => void;
  preview?: AssetPreviewShape;
  disabled?: boolean;
  onBusyChange?: (busy: boolean) => void;
}

/** A catalog media field (poster, backdrop, trailer, thumbnail) filled by uploading a file or pasting a link. */
export const AssetInput: React.FC<AssetInputProps> = ({ label, kind, value, onChange, preview = 'poster', disabled, onBusyChange }) => {
  const [mode, setMode] = useState<'upload' | 'link'>(() => (value && !isMediaServiceUrl(value) ? 'link' : 'upload'));
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [failedFile, setFailedFile] = useState<File | null>(null);
  const [previewFailed, setPreviewFailed] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const uploading = progress !== null;

  useUnloadGuard(uploading);

  useEffect(() => setPreviewFailed(false), [value]);
  useEffect(() => () => controllerRef.current?.abort(), []);

  const upload = async (file: File) => {
    const invalid = typeError(kind, file);
    if (invalid) {
      setError(invalid);
      setFailedFile(null);
      return;
    }
    const controller = new AbortController();
    controllerRef.current = controller;
    setError(null);
    setFailedFile(null);
    setProgress({ phase: 'preparing', uploadedBytes: 0, totalBytes: file.size, percent: 0, partsDone: 0, totalParts: 0 });
    onBusyChange?.(true);
    try {
      const result = await chunkedUpload({
        file,
        purpose: kind === 'image' ? 'IMAGE' : 'TRAILER',
        signal: controller.signal,
        onProgress: setProgress,
      });
      if (!result.file?.url) throw new Error('The media service did not return a file URL.');
      onChange(result.file.url);
    } catch (err) {
      if (!isUploadCancelled(err) && !controller.signal.aborted) {
        setError(errorMessage(err, 'Upload failed'));
        setFailedFile(file);
      }
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null;
      setProgress(null);
      onBusyChange?.(false);
    }
  };

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (file) void upload(file);
  };

  const linkProblem = mode === 'link' ? linkError(label, value) : null;
  const showPreview = !!value && isHttpUrl(value);
  const Icon = kind === 'image' ? ImageIcon : Film;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <label className="block text-xs font-semibold text-slate-300">{label}</label>
        <div className="flex gap-1 bg-slate-950 border border-slate-800 rounded p-0.5">
          <button type="button" disabled={disabled || uploading} onClick={() => setMode('upload')} className={TAB_CLASS(mode === 'upload')}>
            <UploadCloud className="w-3 h-3" />
            Upload file
          </button>
          <button type="button" disabled={disabled || uploading} onClick={() => setMode('link')} className={TAB_CLASS(mode === 'link')}>
            <Link2 className="w-3 h-3" />
            Paste link
          </button>
        </div>
      </div>

      <div className="flex items-start gap-3">
        <div
          className={`${PREVIEW_CLASS[kind === 'trailer' ? 'backdrop' : preview]} shrink-0 rounded bg-slate-950 border border-slate-800 overflow-hidden flex items-center justify-center`}
        >
          {showPreview && !previewFailed ? (
            kind === 'image' ? (
              <img src={value} alt="" className="w-full h-full object-cover" onError={() => setPreviewFailed(true)} />
            ) : (
              <video src={value} controls preload="metadata" className="w-full h-full object-cover bg-black" onError={() => setPreviewFailed(true)} />
            )
          ) : (
            <div className="flex flex-col items-center gap-1 text-slate-600 p-1 text-center">
              <Icon className="w-4 h-4" />
              {showPreview && <span className="text-[9px] leading-tight">Preview unavailable</span>}
            </div>
          )}
        </div>

        <div className="flex-1 min-w-0 space-y-2">
          {mode === 'upload' ? (
            uploading && progress ? (
              <div className="space-y-1.5">
                <UploadProgressBar progress={progress} compact />
                <button type="button" onClick={() => controllerRef.current?.abort()} className={BUTTON_SECONDARY}>
                  <X className="w-3 h-3" />
                  Cancel upload
                </button>
              </div>
            ) : (
              <div className="flex flex-wrap items-center gap-2">
                <input ref={fileInputRef} type="file" accept={ACCEPT[kind]} className="hidden" onChange={handleFile} />
                <button type="button" disabled={disabled} onClick={() => fileInputRef.current?.click()} className={BUTTON_SECONDARY}>
                  <UploadCloud className="w-3 h-3" />
                  {value ? 'Upload a different file' : 'Choose file'}
                </button>
                <span className="text-[10px] text-slate-500">
                  {kind === 'image' ? 'JPEG, PNG or WebP' : 'MP4, WebM or MOV (played as-is)'}
                </span>
              </div>
            )
          ) : (
            <input
              type="url"
              value={value}
              disabled={disabled}
              placeholder="https://…"
              onChange={(e) => onChange(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500 disabled:opacity-50"
            />
          )}

          {linkProblem && <p className="text-[11px] text-rose-300">{linkProblem}</p>}
          {error && (
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-[11px] text-rose-300 break-words">{error}</p>
              {failedFile && (
                <button type="button" disabled={disabled} onClick={() => void upload(failedFile)} className={BUTTON_SECONDARY}>
                  <RotateCcw className="w-3 h-3" />
                  Resume upload
                </button>
              )}
            </div>
          )}

          {value && !uploading && (
            <div className="flex items-center gap-2 min-w-0">
              {mode === 'upload' && (
                <span className="text-[10px] text-slate-500 font-mono truncate" title={value}>
                  {value}
                </span>
              )}
              <button
                type="button"
                disabled={disabled}
                onClick={() => onChange('')}
                className="flex items-center gap-1 px-2 py-0.5 rounded border border-rose-500/30 text-rose-300 hover:bg-rose-500/10 text-[10px] font-semibold transition disabled:opacity-50 shrink-0"
              >
                <Trash2 className="w-3 h-3" />
                Remove
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
