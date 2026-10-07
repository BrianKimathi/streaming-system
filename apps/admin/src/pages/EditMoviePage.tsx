import React, { useMemo, useState } from 'react';
import { ArrowLeft, Clapperboard, Film } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { MediaAsset, Movie } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { ErrorBanner, SuccessBanner } from '../components/common/Feedback';
import { VideoSourceInput } from '../components/media/VideoSourceInput';
import type { PendingVideo } from '../components/media/VideoSourceInput';
import type { UploadProgress } from '../utils/chunkedUpload';
import { useBusyKeys, useUnloadGuard } from '../hooks/useUnloadGuard';
import {
  MovieDetailsFields,
  PUBLISH_WITHOUT_VIDEO_WARNING,
  PublishWarning,
  catalogSaveError,
  movieFormFrom,
  toUpdateMovieRequest,
  validateMovieForm,
} from './CreateMoviePage';
import type { MovieFormState } from './CreateMoviePage';

interface EditMoviePageProps {
  movie: Movie;
  /** Video chosen on the create form; its upload/import starts as soon as this page opens. */
  pendingVideo?: PendingVideo | null;
  onSaved: (movie: Movie) => void;
  onBack: () => void;
}

function detailsKey(form: MovieFormState): string {
  return JSON.stringify({ ...toUpdateMovieRequest(form), status: undefined });
}

export const EditMoviePage: React.FC<EditMoviePageProps> = ({ movie, pendingVideo, onSaved, onBack }) => {
  const [saved, setSaved] = useState<Movie>(movie);
  const [form, setForm] = useState<MovieFormState>(() => movieFormFrom(movie));
  const [asset, setAsset] = useState<MediaAsset | null | undefined>(undefined);
  const [videoUpload, setVideoUpload] = useState<UploadProgress | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const { busy: assetsUploading, setBusy } = useBusyKeys();

  const uploading = assetsUploading || videoUpload !== null;
  useUnloadGuard(uploading);

  const savedForm = useMemo(() => movieFormFrom(saved), [saved]);
  const detailsChanged = detailsKey(form) !== detailsKey(savedForm);
  const statusChanged = form.status !== saved.status;
  const dirty = detailsChanged || statusChanged;

  const videoReady = asset?.status === 'COMPLETED';
  const statusWarning = form.status === 'PUBLISHED' && !videoReady ? PUBLISH_WITHOUT_VIDEO_WARNING : null;

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (assetsUploading) {
      setError('Wait for the artwork and trailer uploads to finish.');
      return;
    }
    const invalid = validateMovieForm(form);
    if (invalid) {
      setError(invalid);
      return;
    }
    if (!dirty) {
      setSuccess('No changes to save.');
      return;
    }

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      let updated: Movie;
      if (detailsChanged) {
        updated = await adminService.updateMovie(saved.id, toUpdateMovieRequest(form));
        audit({ action: 'MOVIE_UPDATED', targetType: 'MOVIE', targetId: saved.id, details: updated.title });
        if (updated.status !== form.status) updated = await adminService.updateMovieStatus(saved.id, form.status);
      } else {
        updated = await adminService.updateMovieStatus(saved.id, form.status);
      }
      if (statusChanged) {
        audit({
          action: 'CONTENT_STATUS_CHANGED',
          targetType: 'MOVIE',
          targetId: saved.id,
          details: `${saved.status} -> ${updated.status}`,
        });
      }
      setSaved(updated);
      setForm(movieFormFrom(updated));
      onSaved(updated);
      setSuccess(`"${updated.title}" saved (status ${updated.status}).`);
    } catch (err) {
      setError(
        detailsChanged
          ? catalogSaveError(err, 'Failed to save movie', 'PUT /catalog/admin/movies/{id}')
          : errorMessage(err, 'Failed to update status')
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
        <button
          type="button"
          onClick={onBack}
          disabled={uploading || saving}
          title={uploading ? 'Wait for uploads to finish before leaving' : 'Back to catalog'}
          className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition disabled:opacity-50"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        {saved.posterUrl ? (
          <img src={saved.posterUrl} alt="" className="w-10 h-14 rounded object-cover bg-slate-800 border border-slate-700 shrink-0" />
        ) : (
          <div className="w-10 h-14 rounded bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0">
            <Film className="w-4 h-4 text-slate-600" />
          </div>
        )}
        <div className="min-w-0">
          <h3 className="text-lg font-bold text-white truncate flex items-center gap-2">
            {saved.title}
            <StatusBadge status={saved.status} />
          </h3>
          <p className="text-[10px] text-slate-500 font-mono">{saved.id}</p>
        </div>
      </div>

      <section className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-3">
        <div>
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <Clapperboard className="w-4 h-4 text-red-500" />
            Movie Video
          </h4>
          <p className="text-[11px] text-slate-500 mt-1">
            The video is stored in object storage and transcoded to adaptive HLS (480p/720p/1080p). Subscribers can stream it once it
            is COMPLETED and the movie is PUBLISHED.
          </p>
        </div>
        <VideoSourceInput
          contentId={saved.id}
          contentLabel={`Movie "${saved.title}"`}
          auditTargetType="MOVIE"
          onAssetChange={setAsset}
          onUploadChange={setVideoUpload}
          autoStart={pendingVideo ?? null}
        />
        <PublishWarning
          message={
            saved.status === 'PUBLISHED' && asset !== undefined && !videoReady
              ? `This movie is PUBLISHED. ${PUBLISH_WITHOUT_VIDEO_WARNING}.`
              : null
          }
        />
      </section>

      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <form onSubmit={handleSave} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
        <MovieDetailsFields
          form={form}
          onChange={(patch) => {
            setSuccess(null);
            setForm((f) => ({ ...f, ...patch }));
          }}
          disabled={saving}
          setBusy={setBusy}
          statusLabel="Status"
          statusWarning={asset === undefined ? null : statusWarning}
        />

        <div className="flex items-center justify-end gap-3 border-t border-slate-800 pt-4">
          <button
            type="button"
            onClick={() => {
              setForm(savedForm);
              setError(null);
              setSuccess(null);
            }}
            disabled={saving || assetsUploading || !dirty}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            Discard changes
          </button>
          <button
            type="submit"
            disabled={saving || assetsUploading || !dirty}
            className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {saving ? 'Saving…' : assetsUploading ? 'Waiting for uploads…' : 'Save changes'}
          </button>
        </div>
      </form>
    </div>
  );
};
