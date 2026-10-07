import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, CheckCircle2, Copy, FileVideo, HardDrive, Loader2, RefreshCw, RotateCcw, UploadCloud } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { API_BASE_URL, errorMessage } from '../api/client';
import type { Episode, MediaAsset, MediaStats, Movie, TVShow } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatCard } from '../components/common/StatCard';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import {
  MEDIA_POLL_INTERVAL_MS,
  UploadLimitsNote,
  UploadProgressBar,
  isAssetActive as isActive,
  oversizeError,
} from '../components/common/VideoUpload';
import { formatBytes, formatDateTime, formatDuration, formatNumber, shortId } from '../utils/format';

const SHOW_DETAIL_CONCURRENCY = 4;
const LOOKUP_BATCH = 100;

function episodeLabel(showTitle: string | undefined, episode: Episode): string {
  return `${showTitle ?? 'Unknown show'} · S${episode.seasonNumber}E${episode.episodeNumber} · ${episode.title}`;
}

const API_ORIGIN = (() => {
  try {
    return new URL(API_BASE_URL, window.location.origin).origin;
  } catch {
    return '';
  }
})();

function playlistHref(path: string): string {
  return /^https?:\/\//i.test(path) ? path : `${API_ORIGIN}${path.startsWith('/') ? '' : '/'}${path}`;
}

export const MediaPipelinePage: React.FC = () => {
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [stats, setStats] = useState<MediaStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);

  const [movies, setMovies] = useState<Movie[]>([]);
  const [shows, setShows] = useState<TVShow[]>([]);
  const [titlesLoading, setTitlesLoading] = useState(true);
  const [titlesError, setTitlesError] = useState<string | null>(null);

  const [episodeLabels, setEpisodeLabels] = useState<Record<string, string>>({});
  const [labelError, setLabelError] = useState<string | null>(null);
  const [labelRun, setLabelRun] = useState(0);
  const lookedUpIdsRef = useRef(new Set<string>());
  const fetchedShowIdsRef = useRef(new Set<string>());
  const lookupTrustedRef = useRef(true);
  const resolvingRef = useRef(false);
  const rerunRequestedRef = useRef(false);
  const aliveRef = useRef(true);

  const [contentId, setContentId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [retryingIds, setRetryingIds] = useState<string[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    try {
      setStats(await adminService.getMediaStats());
      setStatsError(null);
    } catch (err) {
      setStatsError(errorMessage(err, 'Failed to load media stats'));
    }
  }, []);

  const loadAssets = useCallback(
    async (silent: boolean) => {
      if (!silent) setLoading(true);
      try {
        setAssets(await adminService.getMediaAssets());
        setError(null);
      } catch (err) {
        setError(errorMessage(err, 'Failed to load media assets'));
      } finally {
        if (!silent) setLoading(false);
      }
      await loadStats();
    },
    [loadStats]
  );

  const loadTitles = useCallback(async () => {
    setTitlesLoading(true);
    setTitlesError(null);
    try {
      const [movieList, showList] = await Promise.all([adminService.getMovies(), adminService.getTVShows()]);
      setMovies(movieList);
      setShows(showList);
    } catch (err) {
      setTitlesError(errorMessage(err, 'Failed to load catalog titles'));
    } finally {
      setTitlesLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAssets(false);
    loadTitles();
  }, [loadAssets, loadTitles]);

  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  const hasActiveAssets = assets.some(isActive);

  useEffect(() => {
    if (!hasActiveAssets) return undefined;
    const timer = window.setInterval(() => {
      loadAssets(true);
    }, MEDIA_POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [hasActiveAssets, loadAssets]);

  // Episode ids can't be labelled from the movie/show lists. Resolve them with one public lookup
  // (covers PUBLISHED shows), then fetch admin details only for the remaining shows, a few at a time,
  // stopping as soon as every asset has a label. Results are cached across polls.
  useEffect(() => {
    if (titlesLoading || titlesError) return;
    if (resolvingRef.current) {
      rerunRequestedRef.current = true;
      return;
    }
    const known = new Set<string>([...movies.map((m) => m.id), ...shows.map((s) => s.id), ...Object.keys(episodeLabels)]);
    const unlabeled = [...new Set(assets.map((a) => a.contentId))].filter((id) => !known.has(id));
    if (unlabeled.length === 0) return;

    const showTitles = new Map(shows.map((s) => [s.id, s.title]));
    const toLookUp = unlabeled.filter((id) => !lookedUpIdsRef.current.has(id));
    const unfetchedShows = shows.filter((s) => !fetchedShowIdsRef.current.has(s.id));
    if (toLookUp.length === 0 && unfetchedShows.length === 0) return;

    resolvingRef.current = true;
    (async () => {
      const found: Record<string, string> = {};
      const remaining = new Set(unlabeled);
      const addEpisodes = (episodes: Episode[]) =>
        episodes.forEach((ep) => {
          found[ep.id] = episodeLabel(showTitles.get(ep.tvShowId), ep);
          remaining.delete(ep.id);
        });

      for (let i = 0; i < toLookUp.length; i += LOOKUP_BATCH) {
        const batch = toLookUp.slice(i, i + LOOKUP_BATCH);
        batch.forEach((id) => lookedUpIdsRef.current.add(id));
        try {
          const result = await adminService.lookupTitles(batch);
          addEpisodes(result.episodes ?? []);
        } catch (err) {
          lookupTrustedRef.current = false;
          console.warn('Catalog lookup failed; falling back to per-show details', err);
        }
      }

      // A successful lookup already returned every episode of PUBLISHED shows.
      const candidates = unfetchedShows.filter((s) => !lookupTrustedRef.current || s.status !== 'PUBLISHED');
      const failures: string[] = [];
      for (let i = 0; i < candidates.length && remaining.size > 0; i += SHOW_DETAIL_CONCURRENCY) {
        const batch = candidates.slice(i, i + SHOW_DETAIL_CONCURRENCY);
        const results = await Promise.allSettled(batch.map((s) => adminService.getTVShowDetail(s.id)));
        results.forEach((result, idx) => {
          const show = batch[idx];
          // Failed shows are also marked so polling doesn't hammer them; "Retry" clears the cache.
          fetchedShowIdsRef.current.add(show.id);
          if (result.status === 'fulfilled') {
            addEpisodes((result.value.seasons ?? []).flatMap((season) => season.episodes ?? []));
          } else {
            failures.push(`"${show.title}": ${errorMessage(result.reason, 'request failed')}`);
          }
        });
      }

      resolvingRef.current = false;
      if (!aliveRef.current) return;
      if (failures.length > 0) setLabelError(`Some episode labels could not be resolved (${failures.join('; ')}).`);
      if (Object.keys(found).length > 0) setEpisodeLabels((prev) => ({ ...prev, ...found }));
      if (rerunRequestedRef.current) {
        rerunRequestedRef.current = false;
        setLabelRun((n) => n + 1);
      }
    })();
  }, [assets, movies, shows, titlesLoading, titlesError, episodeLabels, labelRun]);

  const resetLabelCaches = () => {
    fetchedShowIdsRef.current.clear();
    lookedUpIdsRef.current.clear();
    lookupTrustedRef.current = true;
  };

  const retryLabels = () => {
    resetLabelCaches();
    setLabelError(null);
    setLabelRun((n) => n + 1);
  };

  const refreshAssets = () => {
    resetLabelCaches();
    loadAssets(false);
  };

  const titleById = useMemo(() => {
    const map = new Map<string, string>(Object.entries(episodeLabels));
    movies.forEach((m) => map.set(m.id, `Movie: ${m.title}`));
    shows.forEach((s) => map.set(s.id, `Show: ${s.title}`));
    return map;
  }, [movies, shows, episodeLabels]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0] ?? null;
    setUploadError(null);
    setUploadSuccess(null);
    const tooLarge = selected ? oversizeError(selected) : null;
    if (tooLarge) {
      setUploadError(tooLarge);
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    setFile(selected);
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!contentId || !file) return;
    setUploading(true);
    setProgress(0);
    setUploadError(null);
    setUploadSuccess(null);
    try {
      const asset = await adminService.uploadMedia(contentId, file, setProgress);
      setAssets((list) => [asset, ...list.filter((a) => a.id !== asset.id)]);
      audit({ action: 'MEDIA_UPLOADED', targetType: 'MEDIA', targetId: contentId, details: file.name });
      setUploadSuccess(
        `Uploaded "${file.name}" for ${titleById.get(contentId) ?? contentId}. Status: ${asset.status}. Transcoding runs in the background.`
      );
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      loadStats();
    } catch (err) {
      setUploadError(errorMessage(err, 'Upload failed'));
    } finally {
      setUploading(false);
    }
  };

  const handleRetry = async (asset: MediaAsset) => {
    setRetryingIds((ids) => [...ids, asset.id]);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.retryTranscode(asset.id);
      setAssets((list) => list.map((a) => (a.id === asset.id ? updated : a)));
      audit({
        action: 'MEDIA_TRANSCODE_RETRIED',
        targetType: 'MEDIA',
        targetId: asset.id,
        details: asset.originalFilename ?? asset.contentId,
      });
      setActionSuccess(`Transcode re-queued for ${asset.originalFilename ?? shortId(asset.id)} (status: ${updated.status}).`);
      loadStats();
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to retry transcode'));
    } finally {
      setRetryingIds((ids) => ids.filter((x) => x !== asset.id));
    }
  };

  const handleCopy = async (asset: MediaAsset, url: string) => {
    try {
      await navigator.clipboard.writeText(url);
      setCopiedId(asset.id);
    } catch (err) {
      setActionError(errorMessage(err, 'Could not copy to clipboard'));
    }
  };

  const count = (status: string) => stats?.countByStatus?.[status] ?? 0;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Assets"
          value={formatNumber(stats?.totalAssets)}
          subtitle={stats ? `${formatBytes(stats.totalSourceBytes)} of source video` : statsError ?? undefined}
          icon={HardDrive}
          unavailable={!stats}
        />
        <StatCard
          title="Ready (HLS)"
          value={formatNumber(count('COMPLETED'))}
          subtitle={stats ? `${formatDuration(stats.totalReadyDurationSeconds)} playable` : undefined}
          icon={CheckCircle2}
          iconBgColor="bg-emerald-500/10 text-emerald-400"
          unavailable={!stats}
        />
        <StatCard
          title="In Progress"
          value={formatNumber(count('UPLOADING') + count('PROCESSING'))}
          subtitle={stats ? `${formatNumber(count('UPLOADING'))} uploading · ${formatNumber(count('PROCESSING'))} transcoding` : undefined}
          icon={Loader2}
          iconBgColor="bg-amber-500/10 text-amber-400"
          unavailable={!stats}
        />
        <StatCard
          title="Failed"
          value={formatNumber(count('FAILED'))}
          icon={AlertTriangle}
          iconBgColor="bg-rose-500/10 text-rose-400"
          unavailable={!stats}
        />
      </div>

      <form onSubmit={handleUpload} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
        <div>
          <h3 className="text-sm font-bold text-white flex items-center gap-2">
            <UploadCloud className="w-4 h-4 text-red-500" />
            Upload Source Video
          </h3>
          <p className="text-xs text-slate-400 mt-1">
            The file is uploaded to the media service, then transcoded to HLS with ffmpeg in the background. Server limit: 500 MB.
          </p>
          <UploadLimitsNote />
          <p className="text-[11px] text-slate-500 mt-1">
            TV episode videos are uploaded per episode from Catalog → TV Shows → Manage episodes.
          </p>
        </div>

        <fieldset disabled={uploading} className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Catalog Title</label>
            {titlesError ? (
              <ErrorBanner message={titlesError} onRetry={loadTitles} />
            ) : (
              <select
                required
                value={contentId}
                onChange={(e) => setContentId(e.target.value)}
                disabled={titlesLoading}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500 disabled:opacity-50"
              >
                <option value="">
                  {titlesLoading
                    ? 'Loading titles…'
                    : movies.length + shows.length === 0
                      ? 'No titles in catalog — create one first'
                      : 'Select a movie or show'}
                </option>
                {movies.length > 0 && (
                  <optgroup label="Movies">
                    {movies.map((m) => (
                      <option key={m.id} value={m.id}>
                        Movie: {m.title} ({m.status})
                      </option>
                    ))}
                  </optgroup>
                )}
                {shows.length > 0 && (
                  <optgroup label="TV Shows">
                    {shows.map((s) => (
                      <option key={s.id} value={s.id}>
                        Show: {s.title} ({s.status})
                      </option>
                    ))}
                  </optgroup>
                )}
              </select>
            )}
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Video File</label>
            <input
              ref={fileInputRef}
              type="file"
              accept="video/*"
              required
              onChange={handleFileChange}
              className="w-full text-xs text-slate-300 file:mr-3 file:px-3 file:py-2 file:rounded-lg file:border-0 file:bg-slate-800 file:text-slate-200 file:text-xs file:font-semibold hover:file:bg-slate-700 disabled:opacity-50"
            />
            {file && <p className="text-[11px] text-slate-500 mt-1">{file.name} · {formatBytes(file.size)}</p>}
          </div>
        </fieldset>

        {uploading && <UploadProgressBar progress={progress} />}

        <ErrorBanner message={uploadError} />
        <SuccessBanner message={uploadSuccess} />

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={uploading || !contentId || !file}
            className="flex items-center gap-2 px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            <UploadCloud className="w-4 h-4" />
            {uploading ? 'Uploading…' : 'Upload & Transcode'}
          </button>
        </div>
      </form>

      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-bold text-white">Media Assets</h3>
          <p className="text-[11px] text-slate-500">
            {hasActiveAssets ? 'Auto-refreshing every 10s while assets are uploading or transcoding.' : 'Newest first.'}
          </p>
        </div>
        <button
          onClick={refreshAssets}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <ErrorBanner message={error} onRetry={() => loadAssets(false)} />
      <ErrorBanner message={labelError} onRetry={retryLabels} />
      <ErrorBanner message={actionError} />
      <SuccessBanner message={actionSuccess} />

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        {loading && assets.length === 0 ? (
          <LoadingState label="Loading media assets…" />
        ) : assets.length === 0 ? (
          error ? (
            <EmptyState title="Media assets unavailable" hint="The asset list could not be loaded. See the error above." />
          ) : (
            <EmptyState title="No media assets yet" hint="Upload a source video for a catalog title above to start the HLS pipeline." />
          )
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-6 py-4">Source File & Title</th>
                  <th className="px-6 py-4">Status</th>
                  <th className="px-6 py-4">HLS Master Playlist</th>
                  <th className="px-6 py-4">Duration</th>
                  <th className="px-6 py-4">Size</th>
                  <th className="px-6 py-4">Created / Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {assets.map((asset) => {
                  const retrying = retryingIds.includes(asset.id);
                  const url = asset.masterPlaylistUrl ? playlistHref(asset.masterPlaylistUrl) : null;
                  return (
                    <tr key={asset.id} className="hover:bg-slate-800/40 transition align-top">
                      <td className="px-6 py-4">
                        <div className="flex items-start gap-2">
                          <FileVideo className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
                          <div className="min-w-0">
                            <span className="font-semibold text-white block break-all">{asset.originalFilename ?? '—'}</span>
                            <span className="text-[11px] text-slate-400 block">
                              {titleById.get(asset.contentId) ?? (titlesLoading ? 'Loading title…' : 'Unknown title')}
                            </span>
                            <span className="text-[10px] text-slate-500 font-mono">{asset.contentId}</span>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4 space-y-2">
                        <StatusBadge status={asset.status} />
                        {asset.status === 'FAILED' && (
                          <div className="space-y-2 max-w-xs">
                            {asset.failureReason && (
                              <p className="text-[11px] text-rose-300 break-words">{asset.failureReason}</p>
                            )}
                            <button
                              onClick={() => handleRetry(asset)}
                              disabled={retrying}
                              className="flex items-center gap-1.5 px-2.5 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-800 text-[11px] font-semibold transition disabled:opacity-50"
                            >
                              <RotateCcw className={`w-3 h-3 ${retrying ? 'animate-spin' : ''}`} />
                              {retrying ? 'Retrying…' : 'Retry transcode'}
                            </button>
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-4">
                        {url ? (
                          <div className="flex items-start gap-2 max-w-sm">
                            <span className="font-mono text-[11px] text-slate-400 break-all">{url}</span>
                            <button
                              onClick={() => handleCopy(asset, url)}
                              title="Copy URL"
                              className="p-1 rounded text-slate-400 hover:text-white hover:bg-slate-800 transition shrink-0"
                            >
                              {copiedId === asset.id ? (
                                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        ) : (
                          <span className="text-slate-600">Not available yet</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-slate-400">{formatDuration(asset.durationSeconds)}</td>
                      <td className="px-6 py-4 text-slate-400">{formatBytes(asset.fileSizeBytes)}</td>
                      <td className="px-6 py-4 text-slate-400 whitespace-nowrap">
                        <span className="block">{formatDateTime(asset.createdAt)}</span>
                        <span className="block text-[10px] text-slate-500">{formatDateTime(asset.updatedAt)}</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
