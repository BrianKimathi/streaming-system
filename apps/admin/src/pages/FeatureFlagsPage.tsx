import React, { useCallback, useEffect, useState } from 'react';
import { ToggleLeft, ToggleRight, Sliders, Percent, Plus, RefreshCw, Loader2 } from 'lucide-react';
import { adminService } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { FeatureFlag } from '../types';
import { Modal } from '../components/common/Modal';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDateTime } from '../utils/format';

const inputClass =
  'w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500';

function clampPercentage(value: number): number {
  if (Number.isNaN(value)) return 0;
  return Math.max(0, Math.min(100, Math.round(value)));
}

/** Returns the trimmed reason, `undefined` for an empty reason, or `null` when the admin cancelled. */
function askReason(message: string): string | undefined | null {
  const input = window.prompt(`${message}\n\nOptional reason for the audit log:`, '');
  if (input === null) return null;
  return input.trim() || undefined;
}

interface CreateForm {
  flagKey: string;
  description: string;
  enabled: boolean;
  targetPercentage: number;
}

const emptyForm: CreateForm = { flagKey: '', description: '', enabled: false, targetPercentage: 0 };

export const FeatureFlagsPage: React.FC = () => {
  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [percentDrafts, setPercentDrafts] = useState<Record<string, number>>({});
  const [pendingKey, setPendingKey] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<CreateForm>(emptyForm);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setFlags(await adminService.getFeatureFlags());
      setPercentDrafts({});
    } catch (err) {
      setError(errorMessage(err, 'Failed to load feature flags'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const clearDraft = (flagKey: string) => {
    setPercentDrafts((prev) => {
      const next = { ...prev };
      delete next[flagKey];
      return next;
    });
  };

  const replaceFlag = (updated: FeatureFlag) => {
    setFlags((prev) => prev.map((f) => (f.flagKey === updated.flagKey ? updated : f)));
    clearDraft(updated.flagKey);
  };

  const submitChange = async (flag: FeatureFlag, enabled: boolean, targetPercentage: number, summary: string) => {
    const reason = askReason(`${summary} for ${flag.flagKey}?`);
    if (reason === null) return;

    setPendingKey(flag.flagKey);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.toggleFeatureFlag(flag.flagKey, enabled, targetPercentage, reason);
      replaceFlag(updated);
      setActionSuccess(
        `${updated.flagKey} is now ${updated.enabled ? 'enabled' : 'disabled'} at ${updated.targetPercentage}% rollout.`
      );
    } catch (err) {
      setActionError(errorMessage(err, `Failed to update ${flag.flagKey}`));
    } finally {
      setPendingKey(null);
    }
  };

  const openCreate = () => {
    setForm(emptyForm);
    setCreateError(null);
    setShowCreate(true);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.flagKey.trim()) {
      setCreateError('Flag key is required.');
      return;
    }
    setCreating(true);
    setCreateError(null);
    try {
      const created = await adminService.createFeatureFlag(
        form.flagKey.trim(),
        form.description.trim(),
        form.enabled,
        clampPercentage(form.targetPercentage)
      );
      setFlags((prev) => [...prev, created].sort((a, b) => a.flagKey.localeCompare(b.flagKey)));
      setShowCreate(false);
      setActionError(null);
      setActionSuccess(`Feature flag ${created.flagKey} created.`);
    } catch (err) {
      setCreateError(errorMessage(err, 'Failed to create feature flag'));
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <Sliders className="w-5 h-5 text-red-500" />
            Feature Flags & Controlled Rollouts
          </h3>
          <p className="text-xs text-slate-400">Enable or disable platform capabilities and set progressive rollout percentages.</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => void load()}
            disabled={loading}
            className="px-3 py-2 bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-lg text-xs font-semibold text-slate-300 flex items-center gap-1.5 disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={openCreate}
            className="px-3 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition"
          >
            <Plus className="w-3.5 h-3.5" />
            New flag
          </button>
        </div>
      </div>

      <ErrorBanner message={actionError} />
      <SuccessBanner message={actionSuccess} />

      {error ? (
        <ErrorBanner message={error} onRetry={() => void load()} />
      ) : loading && flags.length === 0 ? (
        <LoadingState label="Loading feature flags…" />
      ) : flags.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl">
          <EmptyState
            title="No feature flags exist yet"
            hint="Create a flag to start gating a capability. Flags are stored by the admin service and every change is audit-logged."
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {flags.map((flag) => {
            const isPending = pendingKey === flag.flagKey;
            const draft = percentDrafts[flag.flagKey];
            const shownPercent = draft ?? flag.targetPercentage;
            const percentChanged = draft !== undefined && draft !== flag.targetPercentage;
            return (
              <div key={flag.flagKey} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h4 className="text-sm font-bold text-white font-mono break-all">{flag.flagKey}</h4>
                    <p className="text-xs text-slate-400">
                      {flag.description || <span className="italic text-slate-600">No description</span>}
                    </p>
                  </div>
                  <button
                    onClick={() =>
                      void submitChange(
                        flag,
                        !flag.enabled,
                        flag.targetPercentage,
                        flag.enabled ? 'Disable' : 'Enable'
                      )
                    }
                    disabled={pendingKey !== null}
                    className={`shrink-0 flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition disabled:opacity-50 ${
                      flag.enabled
                        ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                        : 'bg-slate-800 hover:bg-slate-700 text-slate-400'
                    }`}
                  >
                    {isPending ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : flag.enabled ? (
                      <ToggleRight className="w-4 h-4" />
                    ) : (
                      <ToggleLeft className="w-4 h-4" />
                    )}
                    {flag.enabled ? 'ENABLED' : 'DISABLED'}
                  </button>
                </div>

                <div className="border-t border-slate-800 pt-4 space-y-2">
                  <div className="flex items-center justify-between text-xs text-slate-300">
                    <span className="flex items-center gap-1 text-slate-400">
                      <Percent className="w-3.5 h-3.5" />
                      Rollout percentage
                    </span>
                    <span className="font-bold text-white">
                      {shownPercent}%{percentChanged && <span className="text-amber-400 font-normal"> (unsaved)</span>}
                    </span>
                  </div>
                  <div className="flex items-center gap-3">
                    <input
                      type="range"
                      min={0}
                      max={100}
                      step={1}
                      value={shownPercent}
                      disabled={pendingKey !== null}
                      onChange={(e) =>
                        setPercentDrafts((prev) => ({ ...prev, [flag.flagKey]: clampPercentage(Number(e.target.value)) }))
                      }
                      className="flex-1 h-1.5 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-red-600 disabled:opacity-50"
                    />
                    <input
                      type="number"
                      min={0}
                      max={100}
                      value={shownPercent}
                      disabled={pendingKey !== null}
                      onChange={(e) =>
                        setPercentDrafts((prev) => ({ ...prev, [flag.flagKey]: clampPercentage(Number(e.target.value)) }))
                      }
                      className="w-16 px-2 py-1 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
                    />
                  </div>
                  {percentChanged && (
                    <div className="flex justify-end gap-2">
                      <button
                        onClick={() => clearDraft(flag.flagKey)}
                        disabled={isPending}
                        className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold disabled:opacity-50"
                      >
                        Reset
                      </button>
                      <button
                        onClick={() =>
                          void submitChange(flag, flag.enabled, shownPercent, `Set rollout to ${shownPercent}%`)
                        }
                        disabled={pendingKey !== null}
                        className="px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 disabled:opacity-50"
                      >
                        {isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                        Apply rollout
                      </button>
                    </div>
                  )}
                </div>

                <div className="text-[11px] text-slate-500 flex flex-wrap gap-x-4 gap-y-1">
                  <span>Last modified by {flag.lastModifiedBy ?? '—'}</span>
                  <span>{formatDateTime(flag.lastModifiedAt)}</span>
                  {flag.targetPlan && <span>Plan: {flag.targetPlan}</span>}
                  {flag.targetCountry && <span>Country: {flag.targetCountry}</span>}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <Modal isOpen={showCreate} onClose={() => !creating && setShowCreate(false)} title="New feature flag">
        <form onSubmit={(e) => void handleCreate(e)} className="space-y-4">
          <ErrorBanner message={createError} />
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Flag key</label>
            <input
              required
              value={form.flagKey}
              onChange={(e) => setForm({ ...form, flagKey: e.target.value })}
              placeholder="NEW_PLAYER_UI"
              className={`${inputClass} font-mono`}
            />
            <p className="mt-1 text-[10px] text-slate-500">
              The server upper-cases the key and replaces anything other than letters, digits and underscores.
            </p>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Description</label>
            <textarea
              rows={3}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className={inputClass}
            />
          </div>
          <div className="grid grid-cols-2 gap-3 items-end">
            <label className="flex items-center gap-2 text-xs text-slate-300 font-semibold">
              <input
                type="checkbox"
                checked={form.enabled}
                onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
                className="rounded border-slate-800 text-red-600 focus:ring-red-500 w-4 h-4"
              />
              Enabled on creation
            </label>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Rollout %</label>
              <input
                type="number"
                min={0}
                max={100}
                value={form.targetPercentage}
                onChange={(e) => setForm({ ...form, targetPercentage: clampPercentage(Number(e.target.value)) })}
                className={inputClass}
              />
            </div>
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setShowCreate(false)}
              disabled={creating}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={creating}
              className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 disabled:opacity-50"
            >
              {creating && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
              Create flag
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
