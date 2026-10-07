import React, { useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { ContentStatus, CreateTvShowRequest, TVShow } from '../types';
import { ErrorBanner } from '../components/common/Feedback';
import { GenrePicker, INPUT_CLASS, LABEL_CLASS, StatusSelect, optionalText } from './CreateMoviePage';

interface CreateTvShowPageProps {
  onDone: (show: TVShow) => void;
  onCancel: () => void;
}

export const CreateTvShowPage: React.FC<CreateTvShowPageProps> = ({ onDone, onCancel }) => {
  const [title, setTitle] = useState('');
  const [synopsis, setSynopsis] = useState('');
  const [releaseDate, setReleaseDate] = useState('');
  const [seasonsCount, setSeasonsCount] = useState('');
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
    let seasons: number | undefined;
    if (seasonsCount.trim()) {
      seasons = Number(seasonsCount);
      if (!Number.isInteger(seasons) || seasons < 0 || seasons > 100) {
        setError('Seasons must be a whole number between 0 and 100.');
        return;
      }
    }

    const request: CreateTvShowRequest = { title: trimmedTitle, status };
    const synopsisValue = optionalText(synopsis);
    if (synopsisValue) request.synopsis = synopsisValue;
    if (releaseDate) request.releaseDate = releaseDate;
    if (seasons !== undefined) request.seasonsCount = seasons;
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
      const created = await adminService.createTVShow(request);
      audit({ action: 'TV_SHOW_CREATED', targetType: 'TV_SHOW', targetId: created.id, details: created.title });
      onDone(created);
    } catch (err) {
      setError(errorMessage(err, 'Failed to create TV show'));
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
          <h3 className="text-lg font-bold text-white">Create New TV Show</h3>
          <p className="text-xs text-slate-400">
            The backend creates empty "Season n" records for the season count you enter. Only PUBLISHED titles are visible in the public apps.
          </p>
        </div>
      </div>

      <ErrorBanner message={error} />

      <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
        <fieldset disabled={submitting} className="space-y-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Series Information</h4>

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
            <div>
              <label className={LABEL_CLASS}>Maturity Rating</label>
              <select value={maturityRating} onChange={(e) => setMaturityRating(e.target.value)} className={INPUT_CLASS}>
                <option value="">Not set</option>
                <option value="TV-Y">TV-Y</option>
                <option value="TV-Y7">TV-Y7</option>
                <option value="TV-G">TV-G</option>
                <option value="TV-PG">TV-PG</option>
                <option value="TV-14">TV-14</option>
                <option value="TV-MA">TV-MA</option>
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
            {submitting ? 'Creating…' : 'Create TV Show'}
          </button>
        </div>
      </form>
    </div>
  );
};
