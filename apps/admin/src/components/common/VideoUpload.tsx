import React from 'react';
import type { MediaAsset } from '../../types';
import type { UploadProgress } from '../../utils/chunkedUpload';
import { formatBytes } from '../../utils/format';

export const MEDIA_POLL_INTERVAL_MS = 5_000;

export const isAssetActive = (asset: MediaAsset) => asset.status === 'PROCESSING' || asset.status === 'UPLOADING';

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

/** Short human label for an asset's state, e.g. "Transcoding 42%" or "Downloading from link". */
export function assetStatusLabel(asset: MediaAsset): string | null {
  if (asset.status === 'PROCESSING') {
    return asset.progressPercent != null ? `Transcoding ${Math.round(asset.progressPercent)}%` : 'Transcoding…';
  }
  if (asset.status === 'UPLOADING') return asset.sourceUrl ? 'Downloading from link…' : 'Receiving upload…';
  return null;
}

export const ProgressBar: React.FC<{ percent: number; compact?: boolean; tone?: 'red' | 'amber' }> = ({
  percent,
  compact,
  tone = 'red',
}) => (
  <div className={`${compact ? 'h-1.5' : 'h-2'} bg-slate-800 rounded-full overflow-hidden`}>
    <div
      className={`h-full transition-all ${tone === 'amber' ? 'bg-amber-500' : 'bg-red-600'}`}
      style={{ width: `${Math.max(0, Math.min(100, percent))}%` }}
    />
  </div>
);

export const UploadProgressBar: React.FC<{ progress: UploadProgress; compact?: boolean }> = ({ progress, compact }) => {
  const label =
    progress.phase === 'preparing'
      ? 'Preparing upload…'
      : progress.phase === 'finalizing'
        ? 'Finishing upload on the server…'
        : `Uploading ${formatBytes(progress.uploadedBytes)} of ${formatBytes(progress.totalBytes)}`;
  return (
    <div className="space-y-1">
      <div className="flex justify-between gap-2 text-[11px] text-slate-400">
        <span className="truncate">{label}</span>
        <span className="shrink-0">
          {progress.percent}%
          {!compact && progress.totalParts > 1 && progress.phase === 'uploading' && (
            <span className="text-slate-500"> · part {Math.min(progress.partsDone + 1, progress.totalParts)}/{progress.totalParts}</span>
          )}
        </span>
      </div>
      <ProgressBar percent={progress.percent} compact={compact} />
    </div>
  );
};
