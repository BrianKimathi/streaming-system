import React, { useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { ContentStatus, CreateTvShowRequest, TVShow, UpdateTvShowRequest } from '../types';
import { ErrorBanner } from '../components/common/Feedback';
import { AssetInput } from '../components/media/AssetInput';
import { useBusyKeys, useUnloadGuard } from '../hooks/useUnloadGuard';
import { linkError } from '../utils/media';
import {
  GenrePicker,
  INPUT_CLASS,
  LABEL_CLASS,
  PublishWarning,
  RatingSelect,
  StatusSelect,
  dateInput,
  optionalText,
} from './CreateMoviePage';

const TV_RATINGS = ['TV-Y', 'TV-Y7', 'TV-G', 'TV-PG', 'TV-14', 'TV-MA'];

export interface TvShowFormState {
  title: string;
  synopsis: string;
  releaseDate: string;
  maturityRating: string;
  posterUrl: string;
  backdropUrl: string;
  trailerUrl: string;
  status: ContentStatus;
  genreIds: string[];
}

export function tvShowFormFrom(show?: TVShow | null): TvShowFormState {
  return {
    title: show?.title ?? '',
    synopsis: show?.synopsis ?? '',
    releaseDate: dateInput(show?.releaseDate),
    maturityRating: show?.maturityRating ?? '',
    posterUrl: show?.posterUrl ?? '',
    backdropUrl: show?.backdropUrl ?? '',
    trailerUrl: show?.trailerUrl ?? '',
    status: show?.status ?? 'DRAFT',
    genreIds: (show?.genres ?? []).map((g) => g.id),
  };
}

export function validateTvShowForm(form: TvShowFormState): string | null {
  if (!form.title.trim()) return 'Title is required.';
  return (
    linkError('Poster', form.posterUrl) ?? linkError('Backdrop', form.backdropUrl) ?? linkError('Trailer', form.trailerUrl)
  );
}

export function toUpdateTvShowRequest(form: TvShowFormState): UpdateTvShowRequest {
  return {
    title: form.title.trim(),
    synopsis: optionalText(form.synopsis) ?? null,
    releaseDate: form.releaseDate || null,
    maturityRating: optionalText(form.maturityRating) ?? null,
    posterUrl: optionalText(form.posterUrl) ?? null,
    backdropUrl: optionalText(form.backdropUrl) ?? null,
    trailerUrl: optionalText(form.trailerUrl) ?? null,
    status: form.status,
    genreIds: [...form.genreIds].sort(),
  };
}

/** Series metadata, artwork, trailer, status and genres; shared by the create and edit pages. */
export const TvShowDetailsFields: React.FC<{
  form: TvShowFormState;
  onChange: (patch: Partial<TvShowFormState>) => void;
  disabled?: boolean;
  setBusy: (key: string, busy: boolean) => void;
  statusLabel: string;
  statusWarning?: string | null;
  extraField?: React.ReactNode;
}> = ({ form, onChange, disabled, setBusy, statusLabel, statusWarning = null, extraField }) => (
  <>
    <fieldset disabled={disabled} className="space-y-4">
      <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Series Information</h4>

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
        {extraField}
        <div>
          <label className={LABEL_CLASS}>Maturity Rating</label>
          <RatingSelect value={form.maturityRating} options={TV_RATINGS} onChange={(maturityRating) => onChange({ maturityRating })} />
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

interface CreateTvShowPageProps {
  onDone: (show: TVShow) => void;
  onCancel: () => void;
}

export const CreateTvShowPage: React.FC<CreateTvShowPageProps> = ({ onDone, onCancel }) => {
  const [form, setForm] = useState<TvShowFormState>(() => tvShowFormFrom(null));
  const [seasonsCount, setSeasonsCount] = useState('');
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
    const invalid = validateTvShowForm(form);
    if (invalid) {
      setError(invalid);
      return;
    }
    let seasons: number | undefined;
    if (seasonsCount.trim()) {
      seasons = Number(seasonsCount);
      if (!Number.isInteger(seasons) || seasons < 0 || seasons > 100) {
        setError('Seasons must be a whole number between 0 and 100.');
        return;
      }
    }

    const full = toUpdateTvShowRequest(form);
    const request: CreateTvShowRequest = { title: full.title, status: full.status };
    if (full.synopsis) request.synopsis = full.synopsis;
    if (full.releaseDate) request.releaseDate = full.releaseDate;
    if (seasons !== undefined) request.seasonsCount = seasons;
    if (full.maturityRating) request.maturityRating = full.maturityRating;
    if (full.posterUrl) request.posterUrl = full.posterUrl;
    if (full.backdropUrl) request.backdropUrl = full.backdropUrl;
    if (full.trailerUrl) request.trailerUrl = full.trailerUrl;
    if (full.genreIds.length > 0) request.genreIds = full.genreIds;

    setSubmitting(true);
    setError(null);
    try {
      const created = await adminService.createTVShow(request);
      audit({ action: 'TV_SHOW_CREATED', targetType: 'TV_SHOW', targetId: created.id, details: created.title });
      onDone(created);
    } catch (err) {
      setError(errorMessage(err, 'Failed to create TV show'));
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
          <h3 className="text-lg font-bold text-white">Create New TV Show</h3>
          <p className="text-xs text-slate-400">
            The backend creates empty "Season n" records for the season count you enter; add episodes and their videos from "Manage
            episodes" afterwards. Only PUBLISHED titles are visible in the public apps.
          </p>
        </div>
      </div>

      <ErrorBanner message={error} />

      <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
        <TvShowDetailsFields
          form={form}
          onChange={(patch) => setForm((f) => ({ ...f, ...patch }))}
          disabled={submitting}
          setBusy={setBusy}
          statusLabel="Initial Status"
          statusWarning={
            form.status === 'PUBLISHED' ? "Viewers can't play any episode until its video is uploaded and finishes processing" : null
          }
          extraField={
            <div>
              <label className={LABEL_CLASS}>Seasons (0–100)</label>
              <input
                type="number"
                min={0}
                max={100}
                step={1}
                value={seasonsCount}
                onChange={(e) => setSeasonsCount(e.target.value)}
                className={INPUT_CLASS}
              />
            </div>
          }
        />

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
            {submitting ? 'Creating…' : assetsUploading ? 'Waiting for uploads…' : 'Create TV Show'}
          </button>
        </div>
      </form>
    </div>
  );
};
