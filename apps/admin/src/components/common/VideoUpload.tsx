import React from 'react';
import { AlertTriangle } from 'lucide-react';
import type { MediaAsset } from '../../types';
import { formatBytes } from '../../utils/format';

/** Mirrors the media service's multipart limit. */
export const MAX_UPLOAD_BYTES = 500 * 1024 * 1024;
export const MEDIA_POLL_INTERVAL_MS = 10_000;

export const isAssetActive = (asset: MediaAsset) => asset.status === 'PROCESSING' || asset.status === 'UPLOADING';

/** Returns a user-facing error if the file exceeds the server limit, otherwise null. */
export function oversizeError(file: File): string | null {
  return file.size > MAX_UPLOAD_BYTES
    ? `"${file.name}" is ${formatBytes(file.size)}; the server limit is 500 MB.`
    : null;
}

function assetTimestamp(asset: MediaAsset): number {
  const time = Date.parse(asset.updatedAt ?? asset.createdAt ?? '');
  return Number.isNaN(time) ? 0 : time;
}

/** Most recent asset per contentId (a title can be re-uploaded, leaving older assets behind). */
export function latestAssetByContent(assets: MediaAsset[]): Map<string, MediaAsset> {
  const map = new Map<string, MediaAsset>();
  for (const asset of assets) {
    const current = map.get(asset.contentId);
    if (!current || assetTimestamp(asset) > assetTimestamp(current)) map.set(asset.contentId, asset);
  }
  return map;
}

export const UploadLimitsNote: React.FC = () => (
  <p className="text-[11px] text-amber-400/90 mt-1 flex items-center gap-1.5">
    <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
    Uploads routed through Cloudflare are limited to ~100 MB on the free plan; larger files may be rejected with HTTP 413.
  </p>
);

export const UploadProgressBar: React.FC<{ progress: number; compact?: boolean }> = ({ progress, compact }) => (
  <div className="space-y-1">
    <div className="flex justify-between gap-2 text-[11px] text-slate-400">
      <span>{progress < 100 ? 'Uploading…' : compact ? 'Waiting for server…' : 'Upload sent, waiting for server response…'}</span>
      <span>{progress}%</span>
    </div>
    <div className={`${compact ? 'h-1.5' : 'h-2'} bg-slate-800 rounded-full overflow-hidden`}>
      <div className="h-full bg-red-600 transition-all" style={{ width: `${progress}%` }} />
    </div>
  </div>
);
