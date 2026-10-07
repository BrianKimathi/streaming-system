import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Copy,
  Eye,
  FileVideo,
  Film,
  HardDrive,
  Image as ImageIcon,
  Loader2,
  RefreshCw,
  RotateCcw,
  Trash2,
  UploadCloud,
} from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { Episode, MediaAsset, MediaFile, MediaStats, Movie, Page, TVShow, UploadPurpose } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatCard } from '../components/common/StatCard';
import { Modal } from '../components/common/Modal';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { MEDIA_POLL_INTERVAL_MS, ProgressBar, assetStatusLabel, isAssetActive as isActive } from '../components/common/VideoUpload';
import { HlsPlayer } from '../components/media/HlsPlayer';
import { VideoSourceInput } from '../components/media/VideoSourceInput';
import { formatBytes, formatDateTime, formatDuration, formatNumber, shortId } from '../utils/format';
import { resolveApiUrl } from '../utils/media';

const SHOW_DETAIL_CONCURRENCY = 4;
const LOOKUP_BATCH = 100;
const FILES_PAGE_SIZE = 20;

const BUTTON_SECONDARY =
  'flex items-center gap-1.5 px-2.5 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-800 text-[11px] font-semibold whitespace-nowrap transition disabled:opacity-50';
const BUTTON_DANGER =
  'flex items-center gap-1.5 px-2.5 py-1 rounded border border-rose-500/30 text-rose-300 hover:bg-rose-500/10 text-[11px] font-semibold whitespace-nowrap transition disabled:opacity-50';
const TAB_CLASS = (active: boolean) =>
  `px-3 py-1.5 rounded-lg text-[11px] font-semibold transition ${
    active ? 'bg-red-600 text-white' : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-white'
  }`;

function episodeLabel(showTitle: string | undefined, episode: Episode): string {
  return `${showTitle ?? 'Unknown show'} · S${episode.seasonNumber}E${episode.episodeNumber} · ${episode.title}`;
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

  const [movieId, setMovieId] = useState('');

  const [busyAssetIds, setBusyAssetIds] = useState<string[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [preview, setPreview] = useState<{ title: string; url: string } | null>(null);

  const [filePurpose, setFilePurpose] = useState<UploadPurpose | ''>('');
  const [filePage, setFilePage] = useState(0);
  const [files, setFiles] = useState<Page<MediaFile> | null>(null);
  const [filesLoading, setFilesLoading] = useState(true);
  const [filesError, setFilesError] = useState<string | null>(null);
  const [deletingFileIds, setDeletingFileIds] = useState<string[]>([]);
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

  const loadFiles = useCallback(async () => {
    setFilesLoading(true);
    try {
      setFiles(await adminService.getMediaFiles({ purpose: filePurpose || undefined, page: filePage, size: FILES_PAGE_SIZE }));
      setFilesError(null);
    } catch (err) {
      setFilesError(errorMessage(err, 'Failed to load uploaded files'));
    } finally {
      setFilesLoading(false);
    }
  }, [filePurpose, filePage]);

  useEffect(() => {
    loadAssets(false);
    loadTitles();
  }, [loadAssets, loadTitles]);

  useEffect(() => {
    loadFiles();
  }, [loadFiles]);

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

  const selectedMovie = movies.find((m) => m.id === movieId) ?? null;

  const markAssetBusy = (id: string, busy: boolean) =>
    setBusyAssetIds((ids) => (busy ? [...ids, id] : ids.filter((x) => x !== id)));

  const clearMessages = () => {
    setActionError(null);
    setActionSuccess(null);
  };

  const assetName = (asset: MediaAsset) => titleById.get(asset.contentId) ?? asset.originalFilename ?? shortId(asset.contentId);

  const handleRetry = async (asset: MediaAsset) => {
    markAssetBusy(asset.id, true);
    clearMessages();
    try {
      const updated = await adminService.retryTranscode(asset.id);
      setAssets((list) => list.map((a) => (a.id === asset.id ? updated : a)));
      audit({
        action: 'MEDIA_TRANSCODE_RETRIED',
        targetType: 'MEDIA',
        targetId: asset.id,
        details: asset.originalFilename ?? asset.contentId,
      });
      setActionSuccess(`Transcode re-queued for ${assetName(asset)} (status: ${updated.status}).`);
      loadStats();
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to retry transcode'));
    } finally {
      markAssetBusy(asset.id, false);
    }
  };

  const handleDeleteAsset = async (asset: MediaAsset) => {
    const name = assetName(asset);
    if (!window.confirm(`Delete the video of ${name}? Its source file and HLS renditions are removed and viewers can no longer play it.`)) {
      return;
    }
    markAssetBusy(asset.id, true);
    clearMessages();
    try {
      await adminService.deleteMediaAsset(asset.id);
      setAssets((list) => list.filter((a) => a.id !== asset.id));
      audit({ action: 'MEDIA_ASSET_DELETED', targetType: 'MEDIA', targetId: asset.id, details: `${name} · ${asset.originalFilename ?? ''}` });
      setActionSuccess(`Video of ${name} deleted.`);
      loadStats();
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to delete the video'));
    } finally {
      markAssetBusy(asset.id, false);
    }
  };

  const handlePreview = async (asset: MediaAsset) => {
    markAssetBusy(asset.id, true);
    clearMessages();
    try {
      const { streamUrl } = await adminService.getAssetPreview(asset.contentId);
      setPreview({ title: assetName(asset), url: resolveApiUrl(streamUrl) });
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to open the preview'));
    } finally {
      markAssetBusy(asset.id, false);
    }
  };

  const handleDeleteFile = async (file: MediaFile) => {
    if (
      !window.confirm(
        `Delete "${file.filename}"? Any poster, backdrop, thumbnail or trailer that still links to it will stop loading in the apps.`
      )
    ) {
      return;
    }
    setDeletingFileIds((ids) => [...ids, file.id]);
    clearMessages();
    try {
      await adminService.deleteMediaFile(file.id);
      audit({ action: 'MEDIA_FILE_DELETED', targetType: 'MEDIA_FILE', targetId: file.id, details: `${file.purpose} · ${file.filename}` });
      setActionSuccess(`"${file.filename}" deleted.`);
      if (files && files.content.length === 1 && filePage > 0) setFilePage((p) => p - 1);
      else loadFiles();
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to delete the file'));
    } finally {
      setDeletingFileIds((ids) => ids.filter((x) => x !== file.id));
    }
  };

  const handleCopy = async (id: string, url: string) => {
    try {
      await navigator.clipboard.writeText(url);
      setCopiedId(id);
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
          subtitle={stats ? `${formatNumber(count('UPLOADING'))} downloading · ${formatNumber(count('PROCESSING'))} transcoding` : undefined}
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

      <section className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
        <div>
          <h3 className="text-sm font-bold text-white flex items-center gap-2">
            <UploadCloud className="w-4 h-4 text-red-500" />
            Movie Video
          </h3>
          <p className="text-xs text-slate-400 mt-1">
            Upload a source video of any size (sent in 32 MB chunks to object storage) or import it from a direct link; it is then
            transcoded to HLS in the background. Episode videos are managed from Catalog → TV Shows → Manage episodes.
          </p>
        </div>
        <div className="max-w-md">
          <label className="block text-xs font-semibold text-slate-300 mb-1">Movie</label>
          {titlesError ? (
            <ErrorBanner message={titlesError} onRetry={loadTitles} />
          ) : (
            <select
              value={movieId}
              onChange={(e) => setMovieId(e.target.value)}
              disabled={titlesLoading}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500 disabled:opacity-50"
            >
              <option value="">
                {titlesLoading ? 'Loading movies…' : movies.length === 0 ? 'No movies in the catalog — create one first' : 'Select a movie'}
              </option>
              {movies.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.title} ({m.status})
                </option>
              ))}
            </select>
          )}
        </div>
        {selectedMovie && (
          <VideoSourceInput
            key={selectedMovie.id}
            contentId={selectedMovie.id}
            contentLabel={`Movie "${selectedMovie.title}"`}
            auditTargetType="MOVIE"
            onAssetChange={() => loadAssets(true)}
          />
        )}
      </section>

      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-bold text-white">Video Assets</h3>
          <p className="text-[11px] text-slate-500">
            {hasActiveAssets ? 'Auto-refreshing every 5s while videos are downloading or transcoding.' : 'Newest first.'}
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
            <EmptyState title="No videos yet" hint="Upload or import a video for a movie above, or for an episode from the catalog." />
          )
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-6 py-4">Source & Title</th>
                  <th className="px-6 py-4">Status</th>
                  <th className="px-6 py-4">Duration</th>
                  <th className="px-6 py-4">Size</th>
                  <th className="px-6 py-4">Created / Updated</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {assets.map((asset) => {
                  const busy = busyAssetIds.includes(asset.id);
                  const label = assetStatusLabel(asset);
                  return (
                    <tr key={asset.id} className="hover:bg-slate-800/40 transition align-top">
                      <td className="px-6 py-4">
                        <div className="flex items-start gap-2">
                          <FileVideo className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
                          <div className="min-w-0 max-w-sm">
                            <span className="font-semibold text-white block break-all">
                              {asset.originalFilename ?? (asset.sourceUrl ? 'Imported video' : '—')}
                            </span>
                            <span className="text-[11px] text-slate-400 block">
                              {titleById.get(asset.contentId) ?? (titlesLoading ? 'Loading title…' : 'Unknown title')}
                            </span>
                            {asset.sourceUrl && (
                              <span className="text-[10px] text-slate-500 block break-all">From {asset.sourceUrl}</span>
                            )}
                            <span className="text-[10px] text-slate-500 font-mono">{asset.contentId}</span>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="space-y-1.5 min-w-[150px] max-w-xs">
                          <StatusBadge status={asset.status} />
                          {label && <p className="text-[11px] text-amber-300">{label}</p>}
                          {asset.status === 'PROCESSING' && asset.progressPercent != null && (
                            <ProgressBar percent={asset.progressPercent} compact tone="amber" />
                          )}
                          {asset.status === 'FAILED' && asset.failureReason && (
                            <p className="text-[11px] text-rose-300 break-words line-clamp-4" title={asset.failureReason}>
                              {asset.failureReason}
                            </p>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-slate-400">{formatDuration(asset.durationSeconds)}</td>
                      <td className="px-6 py-4 text-slate-400">{formatBytes(asset.fileSizeBytes)}</td>
                      <td className="px-6 py-4 text-slate-400 whitespace-nowrap">
                        <span className="block">{formatDateTime(asset.createdAt)}</span>
                        <span className="block text-[10px] text-slate-500">{formatDateTime(asset.updatedAt)}</span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex justify-end gap-2">
                          {asset.status === 'COMPLETED' && (
                            <button onClick={() => handlePreview(asset)} disabled={busy} className={BUTTON_SECONDARY}>
                              <Eye className="w-3 h-3" />
                              Preview
                            </button>
                          )}
                          {asset.status === 'FAILED' && (
                            <button onClick={() => handleRetry(asset)} disabled={busy} className={BUTTON_SECONDARY}>
                              <RotateCcw className={`w-3 h-3 ${busy ? 'animate-spin' : ''}`} />
                              Retry
                            </button>
                          )}
                          {asset.status !== 'PROCESSING' && (
                            <button onClick={() => handleDeleteAsset(asset)} disabled={busy} className={BUTTON_DANGER}>
                              <Trash2 className="w-3 h-3" />
                              Delete
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-bold text-white">Images & Trailers</h3>
          <p className="text-[11px] text-slate-500">Files uploaded for posters, backdrops, thumbnails and trailers (publicly served).</p>
        </div>
        <div className="flex items-center gap-2">
          {(
            [
              ['', 'All'],
              ['IMAGE', 'Images'],
              ['TRAILER', 'Trailers'],
            ] as const
          ).map(([value, text]) => (
            <button
              key={text}
              onClick={() => {
                setFilePurpose(value);
                setFilePage(0);
              }}
              className={TAB_CLASS(filePurpose === value)}
            >
              {text}
            </button>
          ))}
          <button
            onClick={loadFiles}
            disabled={filesLoading}
            className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${filesLoading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      <ErrorBanner message={filesError} onRetry={loadFiles} />

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        {filesLoading && !files ? (
          <LoadingState label="Loading files…" />
        ) : !files || files.content.length === 0 ? (
          filesError ? (
            <EmptyState title="Files unavailable" hint="The file list could not be loaded. See the error above." />
          ) : (
            <EmptyState title="No files uploaded yet" hint="Upload artwork or trailers from the movie and TV show forms." />
          )
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="px-6 py-4">Preview</th>
                    <th className="px-6 py-4">File</th>
                    <th className="px-6 py-4">Size</th>
                    <th className="px-6 py-4">Uploaded</th>
                    <th className="px-6 py-4">Public URL</th>
                    <th className="px-6 py-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {files.content.map((file) => {
                    const deleting = deletingFileIds.includes(file.id);
                    return (
                      <tr key={file.id} className="hover:bg-slate-800/40 transition align-top">
                        <td className="px-6 py-3">
                          {file.purpose === 'IMAGE' ? (
                            <img
                              src={file.url}
                              alt=""
                              loading="lazy"
                              className="w-20 h-12 rounded object-cover bg-slate-800 border border-slate-700"
                            />
                          ) : (
                            <video
                              src={file.url}
                              preload="metadata"
                              controls
                              className="w-32 aspect-video rounded bg-black border border-slate-700"
                            />
                          )}
                        </td>
                        <td className="px-6 py-3">
                          <div className="flex items-start gap-2">
                            {file.purpose === 'IMAGE' ? (
                              <ImageIcon className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
                            ) : (
                              <Film className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
                            )}
                            <div className="min-w-0">
                              <span className="font-semibold text-white block break-all">{file.filename}</span>
                              <span className="text-[10px] text-slate-500">
                                {file.purpose} · {file.contentType}
                              </span>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-3 text-slate-400">{formatBytes(file.sizeBytes)}</td>
                        <td className="px-6 py-3 text-slate-400 whitespace-nowrap">{formatDateTime(file.createdAt)}</td>
                        <td className="px-6 py-3">
                          <div className="flex items-start gap-2 max-w-sm">
                            <span className="font-mono text-[11px] text-slate-400 break-all">{file.url}</span>
                            <button
                              onClick={() => handleCopy(file.id, file.url)}
                              title="Copy URL"
                              className="p-1 rounded text-slate-400 hover:text-white hover:bg-slate-800 transition shrink-0"
                            >
                              {copiedId === file.id ? (
                                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        </td>
                        <td className="px-6 py-3">
                          <div className="flex justify-end">
                            <button onClick={() => handleDeleteFile(file)} disabled={deleting} className={BUTTON_DANGER}>
                              <Trash2 className="w-3 h-3" />
                              {deleting ? 'Deleting…' : 'Delete'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between gap-3 px-6 py-3 border-t border-slate-800 text-[11px] text-slate-400">
              <span>
                {formatNumber(files.totalElements)} file{files.totalElements === 1 ? '' : 's'} · page {files.number + 1} of{' '}
                {Math.max(1, files.totalPages)}
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setFilePage((p) => Math.max(0, p - 1))}
                  disabled={filesLoading || files.number <= 0}
                  className={BUTTON_SECONDARY}
                >
                  <ChevronLeft className="w-3 h-3" />
                  Previous
                </button>
                <button
                  onClick={() => setFilePage((p) => p + 1)}
                  disabled={filesLoading || files.number + 1 >= files.totalPages}
                  className={BUTTON_SECONDARY}
                >
                  Next
                  <ChevronRight className="w-3 h-3" />
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      <Modal isOpen={preview !== null} onClose={() => setPreview(null)} title={preview ? `Preview · ${preview.title}` : 'Preview'}>
        {preview && <HlsPlayer src={preview.url} />}
      </Modal>
    </div>
  );
};
