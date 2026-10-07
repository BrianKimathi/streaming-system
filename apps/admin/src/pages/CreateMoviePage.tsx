import React, { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, Tag } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import { CONTENT_STATUSES } from '../types';
import type { ContentStatus, CreateMovieRequest, Genre, Movie } from '../types';
import { ErrorBanner, LoadingState } from '../components/common/Feedback';

export const INPUT_CLASS =
  'w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500 disabled:opacity-50';
export const LABEL_CLASS = 'block text-xs font-semibold text-slate-300 mb-1';

export function optionalText(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

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

interface CreateMoviePageProps {
  onDone: (movie: Movie) => void;
  onCancel: () => void;
}

export const CreateMoviePage: React.FC<CreateMoviePageProps> = ({ onDone, onCancel }) => {
  const [title, setTitle] = useState('');
  const [synopsis, setSynopsis] = useState('');
  const [releaseDate, setReleaseDate] = useState('');
  const [runtimeMinutes, setRuntimeMinutes] = useState('');
  const [maturityRating, setMaturityRating] = useState('');
  const [posterUrl, setPosterUrl] = useState('');
  const [backdropUrl, setBackdropUrl] = useState('');
  const [trailerUrl, setTrailerUrl] = useState('');
  const [status, setStatus] = useState<ContentStatus>('DRAFT');
  const [genreIds, setGenreIds] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setError('Title is required.');
      return;
    }
    let runtime: number | undefined;
    if (runtimeMinutes.trim()) {
      runtime = Number(runtimeMinutes);
      if (!Number.isInteger(runtime) || runtime <= 0) {
        setError('Runtime must be a positive whole number of minutes.');
        return;
      }
    }

    const request: CreateMovieRequest = { title: trimmedTitle, status };
    const synopsisValue = optionalText(synopsis);
    if (synopsisValue) request.synopsis = synopsisValue;
    if (releaseDate) request.releaseDate = releaseDate;
    if (runtime !== undefined) request.runtimeMinutes = runtime;
    const ratingValue = optionalText(maturityRating);
    if (ratingValue) request.maturityRating = ratingValue;
    const posterValue = optionalText(posterUrl);
    if (posterValue) request.posterUrl = posterValue;
    const backdropValue = optionalText(backdropUrl);
    if (backdropValue) request.backdropUrl = backdropValue;
    const trailerValue = optionalText(trailerUrl);
    if (trailerValue) request.trailerUrl = trailerValue;
    if (genreIds.length > 0) request.genreIds = genreIds;

    setSubmitting(true);
    setError(null);
    try {
      const created = await adminService.createMovie(request);
      audit({ action: 'MOVIE_CREATED', targetType: 'MOVIE', targetId: created.id, details: created.title });
      onDone(created);
    } catch (err) {
      setError(errorMessage(err, 'Failed to create movie'));
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
        <button
          type="button"
          onClick={onCancel}
          disabled={submitting}
          className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition disabled:opacity-50"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <h3 className="text-lg font-bold text-white">Create New Catalog Movie</h3>
          <p className="text-xs text-slate-400">
            Only PUBLISHED titles are visible in the public apps. Upload the video on the Media Pipeline page after creating.
          </p>
        </div>
      </div>

      <ErrorBanner message={error} />

      <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
        <fieldset disabled={submitting} className="space-y-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Movie Details</h4>

          <div>
            <label className={LABEL_CLASS}>Title *</label>
            <input type="text" required maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} className={INPUT_CLASS} />
          </div>

          <div>
            <label className={LABEL_CLASS}>Synopsis</label>
            <textarea rows={4} value={synopsis} onChange={(e) => setSynopsis(e.target.value)} className={INPUT_CLASS} />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className={LABEL_CLASS}>Release Date</label>
              <input type="date" value={releaseDate} onChange={(e) => setReleaseDate(e.target.value)} className={INPUT_CLASS} />
            </div>
            <div>
              <label className={LABEL_CLASS}>Runtime (minutes)</label>
              <input
                type="number"
                min={1}
                step={1}
                value={runtimeMinutes}
                onChange={(e) => setRuntimeMinutes(e.target.value)}
                className={INPUT_CLASS}
              />
            </div>
            <div>
              <label className={LABEL_CLASS}>Maturity Rating</label>
              <select value={maturityRating} onChange={(e) => setMaturityRating(e.target.value)} className={INPUT_CLASS}>
                <option value="">Not set</option>
                <option value="G">G</option>
                <option value="PG">PG</option>
                <option value="PG-13">PG-13</option>
                <option value="R">R</option>
                <option value="NC-17">NC-17</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className={LABEL_CLASS}>Poster URL</label>
              <input type="url" value={posterUrl} onChange={(e) => setPosterUrl(e.target.value)} className={INPUT_CLASS} />
            </div>
            <div>
              <label className={LABEL_CLASS}>Backdrop URL</label>
              <input type="url" value={backdropUrl} onChange={(e) => setBackdropUrl(e.target.value)} className={INPUT_CLASS} />
            </div>
            <div>
              <label className={LABEL_CLASS}>Trailer URL</label>
              <input type="url" value={trailerUrl} onChange={(e) => setTrailerUrl(e.target.value)} className={INPUT_CLASS} />
            </div>
          </div>

          <div className="max-w-xs">
            <label className={LABEL_CLASS}>Initial Status</label>
            <StatusSelect value={status} onChange={setStatus} />
          </div>

          <div>
            <label className={LABEL_CLASS}>Genres</label>
            <GenrePicker selected={genreIds} onChange={setGenreIds} disabled={submitting} />
          </div>
        </fieldset>

        <div className="flex items-center justify-end gap-3 border-t border-slate-800 pt-4">
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {submitting ? 'Creating…' : 'Create Movie'}
          </button>
        </div>
      </form>
    </div>
  );
};
