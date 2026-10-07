import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, Clapperboard, Pencil, Plus, RefreshCw, RotateCcw, Trash2, Tv, UploadCloud } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { Episode, EpisodeRequest, MediaAsset, Season, SeasonRequest, TVShow, TvShowDetail } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import {
  MEDIA_POLL_INTERVAL_MS,
  UploadLimitsNote,
  UploadProgressBar,
  isAssetActive,
  latestAssetByContent,
  oversizeError,
} from '../components/common/VideoUpload';
import { formatDate, formatDuration } from '../utils/format';
import { INPUT_CLASS, LABEL_CLASS, optionalText } from './CreateMoviePage';

interface TvShowEpisodesPageProps {
  show: TVShow;
  onBack: () => void;
}

type Editor =
  | { kind: 'season'; seasonId: string | null }
  | { kind: 'episode'; seasonId: string; episodeId: string | null };

interface SeasonForm {
  seasonNumber: string;
  title: string;
  synopsis: string;
  releaseDate: string;
  posterUrl: string;
}

interface EpisodeForm {
  episodeNumber: string;
  title: string;
  synopsis: string;
  runtimeMinutes: string;
  releaseDate: string;
  thumbnailUrl: string;
}

const BUTTON_SECONDARY =
  'flex items-center gap-1.5 px-2.5 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-800 text-[11px] font-semibold transition disabled:opacity-50';
const BUTTON_DANGER =
  'flex items-center gap-1.5 px-2.5 py-1 rounded border border-rose-500/30 text-rose-300 hover:bg-rose-500/10 text-[11px] font-semibold transition disabled:opacity-50';

const dateInput = (value: string | null) => (value ? value.slice(0, 10) : '');

function sortSeasons(seasons: Season[]): Season[] {
  return [...seasons]
    .map((s) => ({ ...s, episodes: [...(s.episodes ?? [])].sort((a, b) => a.episodeNumber - b.episodeNumber) }))
    .sort((a, b) => a.seasonNumber - b.seasonNumber);
}

function seasonLabel(season: Season): string {
  return `Season ${season.seasonNumber}${season.title ? ` · ${season.title}` : ''}`;
}

function episodeCode(seasonNumber: number, episodeNumber: number): string {
  return `S${seasonNumber}E${episodeNumber}`;
}

function withoutKey<T>(record: Record<string, T>, key: string): Record<string, T> {
  const next = { ...record };
  delete next[key];
  return next;
}

function parseWholeNumber(value: string, label: string, min: number, max: number): number | string {
  const n = Number(value);
  if (!value.trim() || !Number.isInteger(n) || n < min || n > max) return `${label} must be a whole number between ${min} and ${max}.`;
  return n;
}

export const TvShowEpisodesPage: React.FC<TvShowEpisodesPageProps> = ({ show, onBack }) => {
  const [detail, setDetail] = useState<TvShowDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [assetsLoaded, setAssetsLoaded] = useState(false);
  const [assetsLoading, setAssetsLoading] = useState(true);
  const [assetsError, setAssetsError] = useState<string | null>(null);

  const [editor, setEditor] = useState<Editor | null>(null);
  const [seasonForm, setSeasonForm] = useState<SeasonForm | null>(null);
  const [episodeForm, setEpisodeForm] = useState<EpisodeForm | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [busyIds, setBusyIds] = useState<string[]>([]);
  const [retryingIds, setRetryingIds] = useState<string[]>([]);
  const [uploads, setUploads] = useState<Record<string, number>>({});
  const [uploadErrors, setUploadErrors] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const uploadTargetRef = useRef<{ episode: Episode; seasonNumber: number } | null>(null);

  const loadDetail = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await adminService.getTVShowDetail(show.id);
      setDetail({ ...data, seasons: sortSeasons(data.seasons ?? []) });
    } catch (err) {
      setLoadError(errorMessage(err, 'Failed to load seasons and episodes'));
    } finally {
      setLoading(false);
    }
  }, [show.id]);

  const loadAssets = useCallback(async (silent: boolean) => {
    if (!silent) setAssetsLoading(true);
    try {
      setAssets(await adminService.getMediaAssets());
      setAssetsLoaded(true);
      setAssetsError(null);
    } catch (err) {
      setAssetsError(errorMessage(err, 'Failed to load episode video status'));
    } finally {
      if (!silent) setAssetsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDetail();
    loadAssets(false);
  }, [loadDetail, loadAssets]);

  const seasons = useMemo(() => detail?.seasons ?? [], [detail]);
  const allEpisodes = useMemo(() => seasons.flatMap((s) => s.episodes ?? []), [seasons]);
  const latestAssets = useMemo(() => latestAssetByContent(assets), [assets]);

  const hasActiveEpisodeAssets = allEpisodes.some((e) => {
    const asset = latestAssets.get(e.id);
    return asset ? isAssetActive(asset) : false;
  });

  useEffect(() => {
    if (!hasActiveEpisodeAssets) return undefined;
    const timer = window.setInterval(() => {
      loadAssets(true);
    }, MEDIA_POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [hasActiveEpisodeAssets, loadAssets]);

  const readyCount = allEpisodes.filter((e) => latestAssets.get(e.id)?.status === 'COMPLETED').length;
  const uploadsInFlight = Object.keys(uploads).length > 0;

  const markBusy = (id: string, busy: boolean) =>
    setBusyIds((ids) => (busy ? [...ids, id] : ids.filter((x) => x !== id)));

  const clearMessages = () => {
    setActionError(null);
    setSuccess(null);
  };

  const replaceSeasons = (update: (list: Season[]) => Season[]) =>
    setDetail((d) => (d ? { ...d, seasons: sortSeasons(update(d.seasons ?? [])) } : d));

  // --- Editors ---

  const closeEditor = () => {
    setEditor(null);
    setSeasonForm(null);
    setEpisodeForm(null);
    setFormError(null);
  };

  const openSeasonEditor = (season: Season | null) => {
    clearMessages();
    setFormError(null);
    setEpisodeForm(null);
    setEditor({ kind: 'season', seasonId: season?.id ?? null });
    const nextNumber = seasons.reduce((max, s) => Math.max(max, s.seasonNumber), 0) + 1;
    setSeasonForm({
      seasonNumber: String(season?.seasonNumber ?? nextNumber),
      title: season?.title ?? '',
      synopsis: season?.synopsis ?? '',
      releaseDate: dateInput(season?.releaseDate ?? null),
      posterUrl: season?.posterUrl ?? '',
    });
  };

  const openEpisodeEditor = (season: Season, episode: Episode | null) => {
    clearMessages();
    setFormError(null);
    setSeasonForm(null);
    setEditor({ kind: 'episode', seasonId: season.id, episodeId: episode?.id ?? null });
    const nextNumber = (season.episodes ?? []).reduce((max, e) => Math.max(max, e.episodeNumber), 0) + 1;
    setEpisodeForm({
      episodeNumber: String(episode?.episodeNumber ?? nextNumber),
      title: episode?.title ?? '',
      synopsis: episode?.synopsis ?? '',
      runtimeMinutes: episode?.runtimeMinutes != null ? String(episode.runtimeMinutes) : '',
      releaseDate: dateInput(episode?.releaseDate ?? null),
      thumbnailUrl: episode?.thumbnailUrl ?? '',
    });
  };

  // --- Seasons ---

  const submitSeason = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editor || editor.kind !== 'season' || !seasonForm) return;
    const seasonNumber = parseWholeNumber(seasonForm.seasonNumber, 'Season number', 0, 1000);
    if (typeof seasonNumber === 'string') {
      setFormError(seasonNumber);
      return;
    }
    const isCreate = editor.seasonId === null;
    // PUT replaces the season, so cleared optional fields are sent as null.
    const empty = isCreate ? undefined : null;
    const body: SeasonRequest = {
      seasonNumber,
      title: optionalText(seasonForm.title) ?? empty,
      synopsis: optionalText(seasonForm.synopsis) ?? empty,
      releaseDate: seasonForm.releaseDate || empty,
      posterUrl: optionalText(seasonForm.posterUrl) ?? empty,
    };

    setSubmitting(true);
    setFormError(null);
    clearMessages();
    try {
      if (editor.seasonId === null) {
        const created = await adminService.createSeason(show.id, body);
        replaceSeasons((list) => [...list.filter((s) => s.id !== created.id), { ...created, episodes: created.episodes ?? [] }]);
        audit({
          action: 'SEASON_CREATED',
          targetType: 'SEASON',
          targetId: created.id,
          details: `${show.title} · Season ${created.seasonNumber}`,
        });
        setSuccess(`Season ${created.seasonNumber} created.`);
      } else {
        const seasonId = editor.seasonId;
        const previous = seasons.find((s) => s.id === seasonId);
        const updated = await adminService.updateSeason(seasonId, body);
        replaceSeasons((list) =>
          list.map((s) => (s.id === seasonId ? { ...updated, episodes: updated.episodes ?? s.episodes } : s))
        );
        audit({
          action: 'SEASON_UPDATED',
          targetType: 'SEASON',
          targetId: updated.id,
          details: `${show.title} · Season ${previous?.seasonNumber ?? '?'} -> Season ${updated.seasonNumber}`,
        });
        setSuccess(`Season ${updated.seasonNumber} updated.`);
      }
      closeEditor();
    } catch (err) {
      setFormError(errorMessage(err, isCreate ? 'Failed to create season' : 'Failed to update season'));
    } finally {
      setSubmitting(false);
    }
  };

  const deleteSeason = async (season: Season) => {
    const episodeCount = season.episodes?.length ?? 0;
    const confirmed = window.confirm(
      `Delete ${seasonLabel(season)} of "${show.title}"?\n\n` +
        (episodeCount > 0
          ? `This also permanently deletes all ${episodeCount} episode${episodeCount === 1 ? '' : 's'} in this season.`
          : 'This season has no episodes.') +
        ' This cannot be undone.'
    );
    if (!confirmed) return;
    markBusy(season.id, true);
    clearMessages();
    try {
      await adminService.deleteSeason(season.id);
      replaceSeasons((list) => list.filter((s) => s.id !== season.id));
      if (editor?.seasonId === season.id) closeEditor();
      audit({
        action: 'SEASON_DELETED',
        targetType: 'SEASON',
        targetId: season.id,
        details: `${show.title} · Season ${season.seasonNumber} (${episodeCount} episode${episodeCount === 1 ? '' : 's'} deleted)`,
      });
      setSuccess(`Season ${season.seasonNumber} and its ${episodeCount} episode${episodeCount === 1 ? '' : 's'} were deleted.`);
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to delete season'));
    } finally {
      markBusy(season.id, false);
    }
  };

  // --- Episodes ---

  const submitEpisode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editor || editor.kind !== 'episode' || !episodeForm) return;
    const episodeNumber = parseWholeNumber(episodeForm.episodeNumber, 'Episode number', 1, 10000);
    if (typeof episodeNumber === 'string') {
      setFormError(episodeNumber);
      return;
    }
    const title = episodeForm.title.trim();
    if (!title) {
      setFormError('Episode title is required.');
      return;
    }
    let runtimeMinutes: number | undefined;
    if (episodeForm.runtimeMinutes.trim()) {
      const parsed = parseWholeNumber(episodeForm.runtimeMinutes, 'Runtime', 1, 1000);
      if (typeof parsed === 'string') {
        setFormError(parsed);
        return;
      }
      runtimeMinutes = parsed;
    }
    const isCreate = editor.episodeId === null;
    const empty = isCreate ? undefined : null;
    const body: EpisodeRequest = {
      episodeNumber,
      title,
      synopsis: optionalText(episodeForm.synopsis) ?? empty,
      runtimeMinutes: runtimeMinutes ?? empty,
      releaseDate: episodeForm.releaseDate || empty,
      thumbnailUrl: optionalText(episodeForm.thumbnailUrl) ?? empty,
    };
    const season = seasons.find((s) => s.id === editor.seasonId);
    const seasonNumber = season?.seasonNumber ?? 0;

    setSubmitting(true);
    setFormError(null);
    clearMessages();
    try {
      if (editor.episodeId === null) {
        const created = await adminService.createEpisode(editor.seasonId, body);
        const targetSeasonId = created.seasonId ?? editor.seasonId;
        replaceSeasons((list) =>
          list.map((s) =>
            s.id === targetSeasonId ? { ...s, episodes: [...(s.episodes ?? []).filter((x) => x.id !== created.id), created] } : s
          )
        );
        audit({
          action: 'EPISODE_CREATED',
          targetType: 'EPISODE',
          targetId: created.id,
          details: `${show.title} · ${episodeCode(seasonNumber, created.episodeNumber)} · ${created.title}`,
        });
        setSuccess(`${episodeCode(seasonNumber, created.episodeNumber)} "${created.title}" created.`);
      } else {
        const episodeId = editor.episodeId;
        const updated = await adminService.updateEpisode(episodeId, body);
        replaceSeasons((list) =>
          list.map((s) => ({ ...s, episodes: (s.episodes ?? []).map((x) => (x.id === episodeId ? updated : x)) }))
        );
        audit({
          action: 'EPISODE_UPDATED',
          targetType: 'EPISODE',
          targetId: updated.id,
          details: `${show.title} · ${episodeCode(seasonNumber, updated.episodeNumber)} · ${updated.title}`,
        });
        setSuccess(`${episodeCode(seasonNumber, updated.episodeNumber)} "${updated.title}" updated.`);
      }
      closeEditor();
    } catch (err) {
      setFormError(errorMessage(err, isCreate ? 'Failed to create episode' : 'Failed to update episode'));
    } finally {
      setSubmitting(false);
    }
  };

  const deleteEpisode = async (season: Season, episode: Episode) => {
    const code = episodeCode(season.seasonNumber, episode.episodeNumber);
    if (!window.confirm(`Delete ${code} "${episode.title}" from "${show.title}"? This cannot be undone.`)) return;
    markBusy(episode.id, true);
    clearMessages();
    try {
      await adminService.deleteEpisode(episode.id);
      replaceSeasons((list) =>
        list.map((s) => ({ ...s, episodes: (s.episodes ?? []).filter((x) => x.id !== episode.id) }))
      );
      if (editor?.kind === 'episode' && editor.episodeId === episode.id) closeEditor();
      audit({
        action: 'EPISODE_DELETED',
        targetType: 'EPISODE',
        targetId: episode.id,
        details: `${show.title} · ${code} · ${episode.title}`,
      });
      setSuccess(`${code} "${episode.title}" deleted.`);
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to delete episode'));
    } finally {
      markBusy(episode.id, false);
    }
  };

  // --- Videos ---

  const pickVideo = (season: Season, episode: Episode) => {
    uploadTargetRef.current = { episode, seasonNumber: season.seasonNumber };
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
      fileInputRef.current.click();
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] ?? null;
    const target = uploadTargetRef.current;
    uploadTargetRef.current = null;
    if (!file || !target) return;
    const { episode, seasonNumber } = target;
    const code = episodeCode(seasonNumber, episode.episodeNumber);
    clearMessages();

    const tooLarge = oversizeError(file);
    if (tooLarge) {
      setUploadErrors((m) => ({ ...m, [episode.id]: tooLarge }));
      return;
    }
    setUploadErrors((m) => withoutKey(m, episode.id));
    setUploads((m) => ({ ...m, [episode.id]: 0 }));
    try {
      const asset = await adminService.uploadMedia(episode.id, file, (percent) =>
        setUploads((m) => (episode.id in m ? { ...m, [episode.id]: percent } : m))
      );
      setAssets((list) => [asset, ...list.filter((a) => a.id !== asset.id)]);
      setAssetsLoaded(true);
      audit({
        action: 'EPISODE_VIDEO_UPLOADED',
        targetType: 'EPISODE',
        targetId: episode.id,
        details: `${show.title} · ${code} · ${file.name}`,
      });
      setSuccess(`Uploaded "${file.name}" for ${code}. Status: ${asset.status}. Transcoding runs in the background.`);
    } catch (err) {
      setUploadErrors((m) => ({ ...m, [episode.id]: errorMessage(err, 'Upload failed') }));
    } finally {
      setUploads((m) => withoutKey(m, episode.id));
    }
  };

  const retryTranscode = async (asset: MediaAsset, code: string) => {
    setRetryingIds((ids) => [...ids, asset.id]);
    clearMessages();
    try {
      const updated = await adminService.retryTranscode(asset.id);
      setAssets((list) => list.map((a) => (a.id === asset.id ? updated : a)));
      audit({
        action: 'MEDIA_TRANSCODE_RETRIED',
        targetType: 'MEDIA',
        targetId: asset.id,
        details: `${show.title} · ${code} · ${asset.originalFilename ?? asset.contentId}`,
      });
      setSuccess(`Transcode re-queued for ${code} (status: ${updated.status}).`);
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to retry transcode'));
    } finally {
      setRetryingIds((ids) => ids.filter((x) => x !== asset.id));
    }
  };

  const refresh = () => {
    clearMessages();
    loadDetail();
    loadAssets(false);
  };

  // --- Rendering ---

  const renderSeasonForm = () =>
    seasonForm && (
      <form onSubmit={submitSeason} className="bg-slate-950 border border-slate-800 rounded-lg p-4 space-y-3">
        <h5 className="text-xs font-bold text-slate-300 uppercase tracking-wider">
          {editor?.kind === 'season' && editor.seasonId ? 'Edit season' : 'New season'}
        </h5>
        <fieldset disabled={submitting} className="space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
            <div>
              <label className={LABEL_CLASS}>Season number *</label>
              <input
                type="number"
                min={0}
                step={1}
                required
                value={seasonForm.seasonNumber}
                onChange={(e) => setSeasonForm({ ...seasonForm, seasonNumber: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
            <div className="sm:col-span-2">
              <label className={LABEL_CLASS}>Title</label>
              <input
                type="text"
                maxLength={255}
                value={seasonForm.title}
                onChange={(e) => setSeasonForm({ ...seasonForm, title: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
            <div>
              <label className={LABEL_CLASS}>Release date</label>
              <input
                type="date"
                value={seasonForm.releaseDate}
                onChange={(e) => setSeasonForm({ ...seasonForm, releaseDate: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
          </div>
          <div>
            <label className={LABEL_CLASS}>Synopsis</label>
            <textarea
              rows={2}
              value={seasonForm.synopsis}
              onChange={(e) => setSeasonForm({ ...seasonForm, synopsis: e.target.value })}
              className={INPUT_CLASS}
            />
          </div>
          <div>
            <label className={LABEL_CLASS}>Poster URL</label>
            <input
              type="url"
              value={seasonForm.posterUrl}
              onChange={(e) => setSeasonForm({ ...seasonForm, posterUrl: e.target.value })}
              className={INPUT_CLASS}
            />
          </div>
        </fieldset>
        <ErrorBanner message={formError} />
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={closeEditor}
            disabled={submitting}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {submitting ? 'Saving…' : editor?.kind === 'season' && editor.seasonId ? 'Save season' : 'Create season'}
          </button>
        </div>
      </form>
    );

  const renderEpisodeForm = () =>
    episodeForm && (
      <form onSubmit={submitEpisode} className="bg-slate-950 border border-slate-800 rounded-lg p-4 space-y-3">
        <h5 className="text-xs font-bold text-slate-300 uppercase tracking-wider">
          {editor?.kind === 'episode' && editor.episodeId ? 'Edit episode' : 'New episode'}
        </h5>
        <fieldset disabled={submitting} className="space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
            <div>
              <label className={LABEL_CLASS}>Episode number *</label>
              <input
                type="number"
                min={1}
                step={1}
                required
                value={episodeForm.episodeNumber}
                onChange={(e) => setEpisodeForm({ ...episodeForm, episodeNumber: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
            <div className="sm:col-span-3">
              <label className={LABEL_CLASS}>Title *</label>
              <input
                type="text"
                required
                maxLength={255}
                value={episodeForm.title}
                onChange={(e) => setEpisodeForm({ ...episodeForm, title: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
          </div>
          <div>
            <label className={LABEL_CLASS}>Synopsis</label>
            <textarea
              rows={2}
              value={episodeForm.synopsis}
              onChange={(e) => setEpisodeForm({ ...episodeForm, synopsis: e.target.value })}
              className={INPUT_CLASS}
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className={LABEL_CLASS}>Runtime (minutes)</label>
              <input
                type="number"
                min={1}
                step={1}
                value={episodeForm.runtimeMinutes}
                onChange={(e) => setEpisodeForm({ ...episodeForm, runtimeMinutes: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
            <div>
              <label className={LABEL_CLASS}>Release date</label>
              <input
                type="date"
                value={episodeForm.releaseDate}
                onChange={(e) => setEpisodeForm({ ...episodeForm, releaseDate: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
            <div>
              <label className={LABEL_CLASS}>Thumbnail URL</label>
              <input
                type="url"
                value={episodeForm.thumbnailUrl}
                onChange={(e) => setEpisodeForm({ ...episodeForm, thumbnailUrl: e.target.value })}
                className={INPUT_CLASS}
              />
            </div>
          </div>
        </fieldset>
        <ErrorBanner message={formError} />
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={closeEditor}
            disabled={submitting}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {submitting ? 'Saving…' : editor?.kind === 'episode' && editor.episodeId ? 'Save episode' : 'Create episode'}
          </button>
        </div>
      </form>
    );

  const renderVideoStatus = (season: Season, episode: Episode) => {
    const code = episodeCode(season.seasonNumber, episode.episodeNumber);
    const progress = uploads[episode.id];
    const uploadError = uploadErrors[episode.id];
    const asset = latestAssets.get(episode.id);
    let body: React.ReactNode;
    if (progress !== undefined) {
      body = <UploadProgressBar progress={progress} compact />;
    } else if (!assetsLoaded) {
      body = assetsLoading ? (
        <span className="text-slate-500">Checking…</span>
      ) : (
        <span className="text-slate-500" title={assetsError ?? undefined}>
          Status unavailable
        </span>
      );
    } else if (!asset) {
      body = <span className="text-slate-500">No video</span>;
    } else {
      const retrying = retryingIds.includes(asset.id);
      body = (
        <div className="space-y-1.5">
          <div className="flex items-center gap-2">
            <StatusBadge status={asset.status} />
            {asset.status === 'COMPLETED' && (
              <span className="text-slate-400">{formatDuration(asset.durationSeconds)}</span>
            )}
          </div>
          {asset.status === 'FAILED' && (
            <>
              {asset.failureReason && <p className="text-[11px] text-rose-300 break-words">{asset.failureReason}</p>}
              <button onClick={() => retryTranscode(asset, code)} disabled={retrying} className={BUTTON_SECONDARY}>
                <RotateCcw className={`w-3 h-3 ${retrying ? 'animate-spin' : ''}`} />
                {retrying ? 'Retrying…' : 'Retry transcode'}
              </button>
            </>
          )}
        </div>
      );
    }
    return (
      <div className="space-y-1.5 min-w-[160px] max-w-xs">
        {body}
        {uploadError && <p className="text-[11px] text-rose-300 break-words">{uploadError}</p>}
      </div>
    );
  };

  const renderSeason = (season: Season) => {
    const episodes = season.episodes ?? [];
    const seasonBusy = busyIds.includes(season.id);
    const seasonUploading = episodes.some((e) => e.id in uploads);
    const editingThisSeason = editor?.kind === 'season' && editor.seasonId === season.id;
    const addingEpisodeHere = editor?.kind === 'episode' && editor.seasonId === season.id && editor.episodeId === null;

    return (
      <section key={season.id} className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-800 flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h4 className="text-sm font-bold text-white">{seasonLabel(season)}</h4>
            <p className="text-[11px] text-slate-500">
              {episodes.length} episode{episodes.length === 1 ? '' : 's'}
              {season.releaseDate ? ` · Released ${formatDate(season.releaseDate)}` : ''}
            </p>
            {season.synopsis && <p className="text-[11px] text-slate-400 mt-1 max-w-2xl">{season.synopsis}</p>}
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => openEpisodeEditor(season, null)}
              disabled={submitting || seasonBusy}
              className={BUTTON_SECONDARY}
            >
              <Plus className="w-3 h-3" />
              Add episode
            </button>
            <button onClick={() => openSeasonEditor(season)} disabled={submitting || seasonBusy} className={BUTTON_SECONDARY}>
              <Pencil className="w-3 h-3" />
              Edit
            </button>
            <button
              onClick={() => deleteSeason(season)}
              disabled={submitting || seasonBusy || seasonUploading}
              title={seasonUploading ? 'Wait for uploads in this season to finish' : undefined}
              className={BUTTON_DANGER}
            >
              <Trash2 className="w-3 h-3" />
              {seasonBusy ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        </div>

        {editingThisSeason && <div className="p-4 border-b border-slate-800">{renderSeasonForm()}</div>}

        {episodes.length === 0 && !addingEpisodeHere ? (
          <EmptyState title="No episodes in this season yet" hint='Use "Add episode" to create the first one.' />
        ) : (
          episodes.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="px-5 py-3">Episode</th>
                    <th className="px-5 py-3">Runtime</th>
                    <th className="px-5 py-3">Release</th>
                    <th className="px-5 py-3">Video</th>
                    <th className="px-5 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {episodes.map((episode) => {
                    const busy = busyIds.includes(episode.id) || seasonBusy;
                    const uploading = episode.id in uploads;
                    const asset = latestAssets.get(episode.id);
                    const assetActive = asset ? isAssetActive(asset) : false;
                    const editingThis = editor?.kind === 'episode' && editor.episodeId === episode.id;
                    return (
                      <React.Fragment key={episode.id}>
                        <tr className="hover:bg-slate-800/40 transition align-top">
                          <td className="px-5 py-3">
                            <p className="font-semibold text-white">
                              <span className="text-slate-500 font-mono mr-2">
                                {episodeCode(season.seasonNumber, episode.episodeNumber)}
                              </span>
                              {episode.title}
                            </p>
                            {episode.synopsis && (
                              <p className="text-[11px] text-slate-500 max-w-md line-clamp-2">{episode.synopsis}</p>
                            )}
                            <p className="text-[10px] text-slate-600 font-mono">{episode.id}</p>
                          </td>
                          <td className="px-5 py-3 text-slate-400 whitespace-nowrap">
                            {episode.runtimeMinutes != null ? `${episode.runtimeMinutes} min` : '—'}
                          </td>
                          <td className="px-5 py-3 text-slate-400 whitespace-nowrap">{formatDate(episode.releaseDate)}</td>
                          <td className="px-5 py-3">{renderVideoStatus(season, episode)}</td>
                          <td className="px-5 py-3">
                            <div className="flex justify-end gap-2">
                              <button
                                onClick={() => pickVideo(season, episode)}
                                disabled={busy || uploading || assetActive}
                                title={assetActive ? 'A video for this episode is still uploading or transcoding' : undefined}
                                className={BUTTON_SECONDARY}
                              >
                                <UploadCloud className="w-3 h-3" />
                                {uploading ? 'Uploading…' : asset ? 'Replace video' : 'Upload video'}
                              </button>
                              <button
                                onClick={() => openEpisodeEditor(season, episode)}
                                disabled={submitting || busy}
                                className={BUTTON_SECONDARY}
                              >
                                <Pencil className="w-3 h-3" />
                                Edit
                              </button>
                              <button
                                onClick={() => deleteEpisode(season, episode)}
                                disabled={submitting || busy || uploading}
                                className={BUTTON_DANGER}
                              >
                                <Trash2 className="w-3 h-3" />
                                {busyIds.includes(episode.id) ? 'Deleting…' : 'Delete'}
                              </button>
                            </div>
                          </td>
                        </tr>
                        {editingThis && (
                          <tr>
                            <td colSpan={5} className="px-5 py-3">
                              {renderEpisodeForm()}
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )
        )}

        {addingEpisodeHere && <div className="p-4 border-t border-slate-800">{renderEpisodeForm()}</div>}
      </section>
    );
  };

  const title = detail?.title ?? show.title;
  const creatingSeason = editor?.kind === 'season' && editor.seasonId === null;

  return (
    <div className="space-y-6">
      <input ref={fileInputRef} type="file" accept="video/*" className="hidden" onChange={handleFileChange} />

      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={onBack}
            disabled={uploadsInFlight || submitting}
            title={uploadsInFlight ? 'Wait for uploads to finish before leaving' : 'Back to catalog'}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition disabled:opacity-50"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          {(detail?.posterUrl ?? show.posterUrl) ? (
            <img
              src={detail?.posterUrl ?? show.posterUrl ?? ''}
              alt=""
              className="w-10 h-14 rounded object-cover bg-slate-800 border border-slate-700 shrink-0"
            />
          ) : (
            <div className="w-10 h-14 rounded bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0">
              <Tv className="w-4 h-4 text-slate-600" />
            </div>
          )}
          <div className="min-w-0">
            <h3 className="text-lg font-bold text-white truncate flex items-center gap-2">
              {title}
              <StatusBadge status={detail?.status ?? show.status} />
            </h3>
            <p className="text-xs text-slate-400">
              {detail
                ? `${seasons.length} season${seasons.length === 1 ? '' : 's'} · ${allEpisodes.length} episode${allEpisodes.length === 1 ? '' : 's'}` +
                  (assetsLoaded ? ` · ${readyCount} with a playable video` : '')
                : 'Seasons & episodes'}
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <button
            onClick={refresh}
            disabled={loading || assetsLoading}
            className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${loading || assetsLoading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={() => openSeasonEditor(null)}
            disabled={!detail || submitting}
            className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            <Plus className="w-4 h-4" />
            Add season
          </button>
        </div>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl px-5 py-4">
        <p className="text-xs text-slate-300 flex items-center gap-2">
          <Clapperboard className="w-4 h-4 text-red-500 shrink-0" />
          Each episode is a playable item: its video is uploaded to the media service under the episode id and transcoded to HLS. Server limit: 500 MB.
        </p>
        <UploadLimitsNote />
        {hasActiveEpisodeAssets && (
          <p className="text-[11px] text-slate-500 mt-1">Video status auto-refreshes every 10s while episodes are uploading or transcoding.</p>
        )}
      </div>

      <ErrorBanner message={loadError} onRetry={loadDetail} />
      <ErrorBanner message={assetsError} onRetry={() => loadAssets(false)} />
      <ErrorBanner message={actionError} />
      <SuccessBanner message={success} />

      {creatingSeason && renderSeasonForm()}

      {loading && !detail ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl">
          <LoadingState label="Loading seasons and episodes…" />
        </div>
      ) : !detail ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl">
          <EmptyState title="Seasons unavailable" hint="The show details could not be loaded. See the error above." />
        </div>
      ) : seasons.length === 0 ? (
        !creatingSeason && (
          <div className="bg-slate-900 border border-slate-800 rounded-xl">
            <EmptyState title="This show has no seasons yet" hint='Use "Add season" to create the first season.' />
          </div>
        )
      ) : (
        <div className="space-y-4">{seasons.map(renderSeason)}</div>
      )}
    </div>
  );
};
