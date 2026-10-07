import React, { useMemo, useState } from 'react';
import { ArrowLeft, ListVideo, Tv } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { TVShow } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { ErrorBanner, SuccessBanner } from '../components/common/Feedback';
import { useBusyKeys, useUnloadGuard } from '../hooks/useUnloadGuard';
import { catalogSaveError } from './CreateMoviePage';
import { TvShowDetailsFields, toUpdateTvShowRequest, tvShowFormFrom, validateTvShowForm } from './CreateTvShowPage';
import type { TvShowFormState } from './CreateTvShowPage';

interface EditTvShowPageProps {
  show: TVShow;
  onSaved: (show: TVShow) => void;
  onBack: () => void;
  onManageEpisodes: (show: TVShow) => void;
}

function detailsKey(form: TvShowFormState): string {
  return JSON.stringify({ ...toUpdateTvShowRequest(form), status: undefined });
}

export const EditTvShowPage: React.FC<EditTvShowPageProps> = ({ show, onSaved, onBack, onManageEpisodes }) => {
  const [saved, setSaved] = useState<TVShow>(show);
  const [form, setForm] = useState<TvShowFormState>(() => tvShowFormFrom(show));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const { busy: assetsUploading, setBusy } = useBusyKeys();

  useUnloadGuard(assetsUploading);

  const savedForm = useMemo(() => tvShowFormFrom(saved), [saved]);
  const detailsChanged = detailsKey(form) !== detailsKey(savedForm);
  const statusChanged = form.status !== saved.status;
  const dirty = detailsChanged || statusChanged;

  const handleSave = async (e: React.FormEvent) => {
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
    if (!dirty) return;

    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      let updated: TVShow;
      if (detailsChanged) {
        updated = await adminService.updateTVShow(saved.id, toUpdateTvShowRequest(form));
        audit({ action: 'TV_SHOW_UPDATED', targetType: 'TV_SHOW', targetId: saved.id, details: updated.title });
        if (updated.status !== form.status) updated = await adminService.updateTVShowStatus(saved.id, form.status);
      } else {
        updated = await adminService.updateTVShowStatus(saved.id, form.status);
      }
      if (statusChanged) {
        audit({
          action: 'CONTENT_STATUS_CHANGED',
          targetType: 'TV_SHOW',
          targetId: saved.id,
          details: `${saved.status} -> ${updated.status}`,
        });
      }
      setSaved(updated);
      setForm(tvShowFormFrom(updated));
      onSaved(updated);
      setSuccess(`"${updated.title}" saved (status ${updated.status}).`);
    } catch (err) {
      setError(
        detailsChanged
          ? catalogSaveError(err, 'Failed to save TV show', 'PUT /catalog/admin/tv-shows/{id}')
          : errorMessage(err, 'Failed to update status')
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={onBack}
            disabled={assetsUploading || saving}
            title={assetsUploading ? 'Wait for uploads to finish before leaving' : 'Back to catalog'}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition disabled:opacity-50"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          {saved.posterUrl ? (
            <img src={saved.posterUrl} alt="" className="w-10 h-14 rounded object-cover bg-slate-800 border border-slate-700 shrink-0" />
          ) : (
            <div className="w-10 h-14 rounded bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0">
              <Tv className="w-4 h-4 text-slate-600" />
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
        <button
          type="button"
          onClick={() => onManageEpisodes(saved)}
          disabled={assetsUploading || saving}
          className="flex items-center gap-2 px-4 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-200 rounded-lg text-xs font-semibold transition disabled:opacity-50"
        >
          <ListVideo className="w-4 h-4" />
          Seasons, episodes & videos
        </button>
      </div>

      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <form onSubmit={handleSave} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
        <TvShowDetailsFields
          form={form}
          onChange={(patch) => {
            setSuccess(null);
            setForm((f) => ({ ...f, ...patch }));
          }}
          disabled={saving}
          setBusy={setBusy}
          statusLabel="Status"
          statusWarning={
            form.status === 'PUBLISHED'
              ? "Viewers can only play episodes whose video has finished processing (see Manage episodes)"
              : null
          }
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
