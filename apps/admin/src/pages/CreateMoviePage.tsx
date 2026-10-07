import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { AlertTriangle, ArrowLeft, Clapperboard, Tag } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import { CONTENT_STATUSES } from '../types';
import type { ContentStatus, CreateMovieRequest, Genre, Movie, UpdateMovieRequest } from '../types';
import { ErrorBanner, LoadingState } from '../components/common/Feedback';
import { AssetInput } from '../components/media/AssetInput';
import { PendingVideoPicker, videoLinkError } from '../components/media/VideoSourceInput';
import type { PendingVideo } from '../components/media/VideoSourceInput';
import { useBusyKeys, useUnloadGuard } from '../hooks/useUnloadGuard';
import { linkError } from '../utils/media';

export const INPUT_CLASS =
  'w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500 disabled:opacity-50';
export const LABEL_CLASS = 'block text-xs font-semibold text-slate-300 mb-1';

export const PUBLISH_WITHOUT_VIDEO_WARNING = "Viewers can't play this title until its video finishes processing";

export function optionalText(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

export const dateInput = (value: string | null | undefined) => (value ? value.slice(0, 10) : '');

/** Error text for a failed catalog save; points at the missing endpoint when the route doesn't exist. */
export function catalogSaveError(err: unknown, fallback: string, endpoint: string): string {
  const message = errorMessage(err, fallback);
  const status = axios.isAxiosError(err) ? err.response?.status : undefined;
  return status === 404 || status === 405
    ? `${message}. Saving edited details requires the catalog service endpoint ${endpoint}.`
    : message;
}

export const PublishWarning: React.FC<{ message: string | null }> = ({ message }) =>
  message ? (
    <p className="text-[11px] text-amber-300 flex items-start gap-1.5 mt-1">
      <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-px" />
      {message}
    </p>
  ) : null;

export const GenrePicker: React.FC<{
  selected: string[];
  onChange: (ids: string[]) => void;
  disabled?: boolean;
}> = ({ selected, onChange, disabled }) => {
  const [genres, setGenres] = useState<Genre[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setGenres(await adminService.getGenres());
    } catch (err) {
      setError(errorMessage(err, 'Failed to load genres'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const toggle = (id: string) => {
    onChange(selected.includes(id) ? selected.filter((g) => g !== id) : [...selected, id]);
  };

  if (loading) return <LoadingState label="Loading genres…" />;
  if (error) return <ErrorBanner message={error} onRetry={load} />;
  if (genres.length === 0) {
    return <p className="text-[11px] text-slate-500">No genres exist yet. Create genres on the Genres page first.</p>;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {genres.map((genre) => {
        const active = selected.includes(genre.id);
        return (
          <button
            key={genre.id}
            type="button"
            disabled={disabled}
            onClick={() => toggle(genre.id)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-semibold transition disabled:opacity-50 ${
              active
                ? 'bg-red-600 border-red-600 text-white'
                : 'bg-slate-950 border-slate-800 text-slate-400 hover:text-white hover:border-slate-700'
            }`}
          >
            <Tag className="w-3 h-3" />
            {genre.name}
          </button>
        );
      })}
    </div>
  );
};

export const StatusSelect: React.FC<{
  value: ContentStatus;
  onChange: (status: ContentStatus) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => (
  <select
    value={value}
    disabled={disabled}
    onChange={(e) => onChange(e.target.value as ContentStatus)}
    className={INPUT_CLASS}
  >
    {CONTENT_STATUSES.map((status) => (
      <option key={status} value={status}>
        {status.replace(/_/g, ' ')}
      </option>
    ))}
  </select>
);

export const RatingSelect: React.FC<{ value: string; options: string[]; onChange: (value: string) => void }> = ({
  value,
  options,
  onChange,
}) => (
  <select value={value} onChange={(e) => onChange(e.target.value)} className={INPUT_CLASS}>
    <option value="">Not set</option>
    {value && !options.includes(value) && <option value={value}>{value}</option>}
    {options.map((rating) => (
      <option key={rating} value={rating}>
        {rating}
      </option>
    ))}
  </select>
);

const MOVIE_RATINGS = ['G', 'PG', 'PG-13', 'R', 'NC-17'];

export interface MovieFormState {
  title: string;
  synopsis: string;
  releaseDate: string;
  runtimeMinutes: string;
  maturityRating: string;
  posterUrl: string;
  backdropUrl: string;
  trailerUrl: string;
  status: ContentStatus;
  genreIds: string[];
}

export function movieFormFrom(movie?: Movie | null): MovieFormState {
  return {
    title: movie?.title ?? '',
    synopsis: movie?.synopsis ?? '',
    releaseDate: dateInput(movie?.releaseDate),
    runtimeMinutes: movie?.runtimeMinutes != null ? String(movie.runtimeMinutes) : '',
    maturityRating: movie?.maturityRating ?? '',
    posterUrl: movie?.posterUrl ?? '',
    backdropUrl: movie?.backdropUrl ?? '',
    trailerUrl: movie?.trailerUrl ?? '',
    status: movie?.status ?? 'DRAFT',
    genreIds: (movie?.genres ?? []).map((g) => g.id),
  };
}

export function validateMovieForm(form: MovieFormState): string | null {
  if (!form.title.trim()) return 'Title is required.';
  if (form.runtimeMinutes.trim()) {
    const runtime = Number(form.runtimeMinutes);
    if (!Number.isInteger(runtime) || runtime <= 0) return 'Runtime must be a positive whole number of minutes.';
  }
  return (
    linkError('Poster', form.posterUrl) ?? linkError('Backdrop', form.backdropUrl) ?? linkError('Trailer', form.trailerUrl)
  );
}

export function toUpdateMovieRequest(form: MovieFormState): UpdateMovieRequest {
  return {
    title: form.title.trim(),
    synopsis: optionalText(form.synopsis) ?? null,
    releaseDate: form.releaseDate || null,
    runtimeMinutes: form.runtimeMinutes.trim() ? Number(form.runtimeMinutes) : null,
    maturityRating: optionalText(form.maturityRating) ?? null,
    posterUrl: optionalText(form.posterUrl) ?? null,
    backdropUrl: optionalText(form.backdropUrl) ?? null,
    trailerUrl: optionalText(form.trailerUrl) ?? null,
    status: form.status,
    genreIds: [...form.genreIds].sort(),
  };
}

function toCreateMovieRequest(form: MovieFormState): CreateMovieRequest {
  const full = toUpdateMovieRequest(form);
  const request: CreateMovieRequest = { title: full.title, status: full.status };
  if (full.synopsis) request.synopsis = full.synopsis;
  if (full.releaseDate) request.releaseDate = full.releaseDate;
  if (full.runtimeMinutes != null) request.runtimeMinutes = full.runtimeMinutes;
  if (full.maturityRating) request.maturityRating = full.maturityRating;
  if (full.posterUrl) request.posterUrl = full.posterUrl;
  if (full.backdropUrl) request.backdropUrl = full.backdropUrl;
  if (full.trailerUrl) request.trailerUrl = full.trailerUrl;
  if (full.genreIds.length > 0) request.genreIds = full.genreIds;
  return request;
}

/** Metadata, artwork, trailer, status and genres of a movie; shared by the create and edit pages. */
export const MovieDetailsFields: React.FC<{
  form: MovieFormState;
  onChange: (patch: Partial<MovieFormState>) => void;
  disabled?: boolean;
  setBusy: (key: string, busy: boolean) => void;
  statusLabel: string;
  statusWarning: string | null;
}> = ({ form, onChange, disabled, setBusy, statusLabel, statusWarning }) => (
  <>
    <fieldset disabled={disabled} className="space-y-4">
      <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Movie Details</h4>

      <div>
        <label className={LABEL_CLASS}>Title *</label>
        <input
          type="text"
          required
          maxLength={255}
          value={form.title}
          onChange={(e) => onChange({ title: e.target.value })}
          className={INPUT_CLASS}
        />
      </div>

      <div>
        <label className={LABEL_CLASS}>Synopsis</label>
        <textarea rows={4} value={form.synopsis} onChange={(e) => onChange({ synopsis: e.target.value })} className={INPUT_CLASS} />
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label className={LABEL_CLASS}>Release Date</label>
          <input type="date" value={form.releaseDate} onChange={(e) => onChange({ releaseDate: e.target.value })} className={INPUT_CLASS} />
        </div>
        <div>
          <label className={LABEL_CLASS}>Runtime (minutes)</label>
          <input
            type="number"
            min={1}
            step={1}
            value={form.runtimeMinutes}
            onChange={(e) => onChange({ runtimeMinutes: e.target.value })}
            className={INPUT_CLASS}
          />
        </div>
        <div>
          <label className={LABEL_CLASS}>Maturity Rating</label>
          <RatingSelect value={form.maturityRating} options={MOVIE_RATINGS} onChange={(maturityRating) => onChange({ maturityRating })} />
        </div>
      </div>
    </fieldset>

    <div className="space-y-4 border-t border-slate-800 pt-4">
      <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Artwork & Trailer</h4>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <AssetInput
          label="Poster"
          kind="image"
          preview="poster"
          value={form.posterUrl}
          disabled={disabled}
          onChange={(posterUrl) => onChange({ posterUrl })}
          onBusyChange={(busy) => setBusy('poster', busy)}
        />
        <AssetInput
          label="Backdrop"
          kind="image"
          preview="backdrop"
          value={form.backdropUrl}
          disabled={disabled}
          onChange={(backdropUrl) => onChange({ backdropUrl })}
          onBusyChange={(busy) => setBusy('backdrop', busy)}
        />
        <AssetInput
          label="Trailer"
          kind="trailer"
          value={form.trailerUrl}
          disabled={disabled}
          onChange={(trailerUrl) => onChange({ trailerUrl })}
          onBusyChange={(busy) => setBusy('trailer', busy)}
        />
      </div>
    </div>

    <fieldset disabled={disabled} className="space-y-4 border-t border-slate-800 pt-4">
      <div className="max-w-xs">
        <label className={LABEL_CLASS}>{statusLabel}</label>
        <StatusSelect value={form.status} onChange={(status) => onChange({ status })} />
        <PublishWarning message={statusWarning} />
      </div>

      <div>
        <label className={LABEL_CLASS}>Genres</label>
        <GenrePicker selected={form.genreIds} onChange={(genreIds) => onChange({ genreIds })} disabled={disabled} />
      </div>
    </fieldset>
  </>
);

interface CreateMoviePageProps {
  onDone: (movie: Movie, pendingVideo: PendingVideo | null) => void;
  onCancel: () => void;
}

export const CreateMoviePage: React.FC<CreateMoviePageProps> = ({ onDone, onCancel }) => {
  const [form, setForm] = useState<MovieFormState>(() => movieFormFrom(null));
  const [pendingVideo, setPendingVideo] = useState<PendingVideo | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { busy: assetsUploading, setBusy } = useBusyKeys();

  useUnloadGuard(assetsUploading);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (assetsUploading) {
      setError('Wait for the artwork and trailer uploads to finish.');
      return;
    }
    const invalid = validateMovieForm(form) ?? (pendingVideo?.kind === 'link' ? videoLinkError(pendingVideo.url) : null);
    if (invalid) {
      setError(invalid);
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const created = await adminService.createMovie(toCreateMovieRequest(form));
      audit({ action: 'MOVIE_CREATED', targetType: 'MOVIE', targetId: created.id, details: created.title });
      onDone(created, pendingVideo);
    } catch (err) {
      setError(errorMessage(err, 'Failed to create movie'));
      setSubmitting(false);
    }
  };

  const busy = submitting || assetsUploading;

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
        <button
          type="button"
          onClick={onCancel}
          disabled={busy}
          title={assetsUploading ? 'Wait for uploads to finish before leaving' : undefined}
          className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition disabled:opacity-50"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <h3 className="text-lg font-bold text-white">Create New Catalog Movie</h3>
          <p className="text-xs text-slate-400">
            Upload or link the artwork and trailer, and optionally pick the movie's video: it starts uploading as soon as the movie
            is created. Only PUBLISHED titles are visible in the public apps.
          </p>
        </div>
      </div>

      <ErrorBanner message={error} />

      <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
        <MovieDetailsFields
          form={form}
          onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
          disabled={submitting}
          setBusy={setBusy}
          statusLabel="Initial Status"
          statusWarning={form.status === 'PUBLISHED' ? PUBLISH_WITHOUT_VIDEO_WARNING : null}
        />

        <div className="space-y-3 border-t border-slate-800 pt-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <Clapperboard className="w-4 h-4 text-red-500" />
            Movie Video (optional)
          </h4>
          <PendingVideoPicker value={pendingVideo} onChange={setPendingVideo} disabled={submitting} />
        </div>

        <div className="flex items-center justify-end gap-3 border-t border-slate-800 pt-4">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {submitting ? 'Creating…' : assetsUploading ? 'Waiting for uploads…' : pendingVideo ? 'Create Movie & Start Video' : 'Create Movie'}
          </button>
        </div>
      </form>
    </div>
  );
};
