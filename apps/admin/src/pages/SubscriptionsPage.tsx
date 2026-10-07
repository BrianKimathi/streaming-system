import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { CreditCard, Layers, Plus, RefreshCw, Search, TrendingUp, Users } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type {
  BillingInterval,
  CreatePlanRequest,
  Subscription,
  SubscriptionPlan,
  SubscriptionStats,
  VideoResolution,
} from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatCard } from '../components/common/StatCard';
import { Modal } from '../components/common/Modal';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDate, formatMoney, formatNumber, shortId } from '../utils/format';

const SUBSCRIPTION_STATUSES: Subscription['status'][] = [
  'TRIAL',
  'ACTIVE',
  'PAST_DUE',
  'GRACE_PERIOD',
  'CANCELLED',
  'EXPIRED',
  'SUSPENDED',
];

const RESOLUTION_LABELS: Record<VideoResolution, string> = {
  SD_720P: '720p HD',
  FHD_1080P: '1080p Full HD',
  UHD_4K: '4K Ultra HD',
};

const AUDIO_SUGGESTIONS = ['STANDARD', 'DOLBY_ATMOS'];

const inputClass =
  'w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500';
const labelClass = 'block text-xs font-semibold text-slate-300 mb-1';

interface PlanFormState {
  name: string;
  description: string;
  price: string;
  currency: string;
  billingInterval: BillingInterval;
  maxProfiles: string;
  maxRegisteredDevices: string;
  maxConcurrentStreams: string;
  maxResolution: VideoResolution;
  hdrEnabled: boolean;
  audioQuality: string;
  downloadsEnabled: boolean;
  maxDownloadDevices: string;
  kidsProfilesEnabled: boolean;
}

const EMPTY_FORM: PlanFormState = {
  name: '',
  description: '',
  price: '',
  currency: 'KES',
  billingInterval: 'MONTHLY',
  maxProfiles: '1',
  maxRegisteredDevices: '1',
  maxConcurrentStreams: '1',
  maxResolution: 'SD_720P',
  hdrEnabled: false,
  audioQuality: 'STANDARD',
  downloadsEnabled: false,
  maxDownloadDevices: '0',
  kidsProfilesEnabled: false,
};

function toRequest(form: PlanFormState): CreatePlanRequest | string {
  const name = form.name.trim();
  if (!name) return 'Plan name is required.';
  const price = Number(form.price);
  if (form.price.trim() === '' || !Number.isFinite(price) || price < 0) return 'Price must be a non-negative number.';
  const ints: [keyof PlanFormState, string, number][] = [
    ['maxProfiles', 'Max profiles', 1],
    ['maxRegisteredDevices', 'Max registered devices', 1],
    ['maxConcurrentStreams', 'Max concurrent streams', 1],
    ['maxDownloadDevices', 'Max download devices', 0],
  ];
  for (const [key, label, min] of ints) {
    const value = Number(form[key]);
    if (!Number.isInteger(value) || value < min) return `${label} must be a whole number ≥ ${min}.`;
  }
  return {
    name,
    description: form.description.trim() || null,
    price,
    currency: form.currency.trim().toUpperCase() || 'KES',
    billingInterval: form.billingInterval,
    maxProfiles: Number(form.maxProfiles),
    maxRegisteredDevices: Number(form.maxRegisteredDevices),
    maxConcurrentStreams: Number(form.maxConcurrentStreams),
    maxResolution: form.maxResolution,
    hdrEnabled: form.hdrEnabled,
    audioQuality: form.audioQuality.trim() || 'STANDARD',
    downloadsEnabled: form.downloadsEnabled,
    maxDownloadDevices: form.downloadsEnabled ? Number(form.maxDownloadDevices) : 0,
    kidsProfilesEnabled: form.kidsProfilesEnabled,
  };
}

export const SubscriptionsPage: React.FC = () => {
  const [stats, setStats] = useState<SubscriptionStats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [plansError, setPlansError] = useState<string | null>(null);
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [subsError, setSubsError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [togglingPlanId, setTogglingPlanId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [form, setForm] = useState<PlanFormState>(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    try {
      setStats(await adminService.getSubscriptionStats());
      setStatsError(null);
    } catch (err) {
      setStatsError(errorMessage(err, 'Failed to load subscription stats'));
    }
  }, []);

  const loadPlans = useCallback(async () => {
    try {
      setPlans(await adminService.getAllPlans());
      setPlansError(null);
    } catch (err) {
      setPlansError(errorMessage(err, 'Failed to load subscription plans'));
    }
  }, []);

  const loadSubscriptions = useCallback(async () => {
    try {
      setSubscriptions(await adminService.getSubscriptions());
      setSubsError(null);
    } catch (err) {
      setSubsError(errorMessage(err, 'Failed to load subscriptions'));
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    await Promise.all([loadStats(), loadPlans(), loadSubscriptions()]);
    setLoading(false);
  }, [loadStats, loadPlans, loadSubscriptions]);

  useEffect(() => {
    void load();
  }, [load]);

  const sortedPlans = useMemo(
    () => [...plans].sort((a, b) => a.name.localeCompare(b.name) || b.version - a.version),
    [plans]
  );

  const filteredSubscriptions = useMemo(() => {
    const term = search.trim().toLowerCase();
    return subscriptions.filter(
      (sub) =>
        (statusFilter === 'ALL' || sub.status === statusFilter) &&
        (!term || sub.accountId.toLowerCase().includes(term) || sub.id.toLowerCase().includes(term))
    );
  }, [subscriptions, search, statusFilter]);

  const mrrEntries = stats ? Object.entries(stats.monthlyRecurringRevenueByCurrency ?? {}) : [];

  const handleTogglePlan = async (plan: SubscriptionPlan) => {
    const activate = !plan.active;
    if (
      !activate &&
      !window.confirm(`Retire ${plan.name} v${plan.version}? It will no longer be available to new subscribers.`)
    ) {
      return;
    }
    setTogglingPlanId(plan.id);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.setPlanActive(plan.id, activate);
      setPlans((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
      setActionSuccess(`${updated.name} v${updated.version} is now ${updated.active ? 'active' : 'retired'}.`);
      audit({
        action: updated.active ? 'PLAN_ACTIVATED' : 'PLAN_RETIRED',
        targetType: 'PLAN',
        targetId: updated.id,
        details: `${updated.name} v${updated.version}`,
      });
      void loadStats();
    } catch (err) {
      setActionError(errorMessage(err, `Failed to ${activate ? 'activate' : 'retire'} plan`));
    } finally {
      setTogglingPlanId(null);
    }
  };

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setCreateError(null);
    setIsCreateOpen(true);
  };

  const existingVersion = useMemo(() => {
    const name = form.name.trim().toLowerCase();
    if (!name) return null;
    const matches = plans.filter((p) => p.name.toLowerCase() === name);
    return matches.length ? Math.max(...matches.map((p) => p.version)) : null;
  }, [form.name, plans]);

  const handleCreatePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    const request = toRequest(form);
    if (typeof request === 'string') {
      setCreateError(request);
      return;
    }
    setCreating(true);
    setCreateError(null);
    try {
      const created = await adminService.createSubscriptionPlan(request);
      audit({
        action: 'PLAN_CREATED',
        targetType: 'PLAN',
        targetId: created.id,
        details: `${created.name} v${created.version}`,
      });
      setIsCreateOpen(false);
      setActionError(null);
      setActionSuccess(
        created.version > 1
          ? `Published ${created.name} v${created.version}; the previous version was retired by the server.`
          : `Created plan ${created.name} v${created.version}.`
      );
      await Promise.all([loadPlans(), loadStats()]);
    } catch (err) {
      setCreateError(errorMessage(err, 'Failed to create plan'));
    } finally {
      setCreating(false);
    }
  };

  const setField = <K extends keyof PlanFormState>(key: K, value: PlanFormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">Subscription Plans & Subscribers</h3>
          <p className="text-xs text-slate-400">Versioned plan catalogue, entitlement rules and live subscriber records.</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => void load()}
            disabled={loading}
            className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={openCreate}
            className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
          >
            <Plus className="w-4 h-4" />
            Create Plan / Version
          </button>
        </div>
      </div>

      <ErrorBanner message={actionError} />
      <SuccessBanner message={actionSuccess} />
      <ErrorBanner message={statsError} onRetry={() => void loadStats()} />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Active Subscriptions"
          value={formatNumber(stats?.activeSubscriptions)}
          subtitle={stats ? `${formatNumber(stats.totalSubscriptions)} subscriptions in total` : undefined}
          icon={Users}
          unavailable={!stats && !loading}
        />
        <StatCard
          title="New (Last 30 Days)"
          value={formatNumber(stats?.newLast30Days)}
          icon={TrendingUp}
          iconBgColor="bg-emerald-500/10 text-emerald-400"
          unavailable={!stats && !loading}
        />
        <StatCard
          title="Monthly Recurring Revenue"
          value={
            mrrEntries.length ? mrrEntries.map(([currency, amount]) => formatMoney(amount, currency)).join(' · ') : '—'
          }
          subtitle={stats && !mrrEntries.length ? 'No recurring revenue from active subscriptions' : 'Per currency'}
          icon={CreditCard}
          iconBgColor="bg-indigo-500/10 text-indigo-400"
          unavailable={!stats && !loading}
        />
        <StatCard
          title="Active Plans"
          value={formatNumber(stats?.activePlans)}
          subtitle={plans.length ? `${formatNumber(plans.length)} plan versions in total` : undefined}
          icon={Layers}
          iconBgColor="bg-amber-500/10 text-amber-400"
          unavailable={!stats && !loading}
        />
      </div>

      <section className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-800">
          <h4 className="text-sm font-bold text-white">Active subscribers by plan</h4>
        </div>
        {loading && !stats ? (
          <LoadingState />
        ) : !stats ? (
          <EmptyState title="Subscriber breakdown unavailable" hint="Subscription stats could not be loaded." />
        ) : stats.activeByPlan.length === 0 ? (
          <EmptyState title="No active subscribers yet" />
        ) : (
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="px-6 py-3">Plan</th>
                <th className="px-6 py-3">Version</th>
                <th className="px-6 py-3">Price</th>
                <th className="px-6 py-3 text-right">Active Subscribers</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {[...stats.activeByPlan]
                .sort((a, b) => b.activeSubscriptions - a.activeSubscriptions)
                .map((row) => (
                  <tr key={row.planId} className="hover:bg-slate-800/40 transition">
                    <td className="px-6 py-3 font-semibold text-white">{row.planName}</td>
                    <td className="px-6 py-3 text-slate-400">{row.planVersion !== null ? `v${row.planVersion}` : '—'}</td>
                    <td className="px-6 py-3">
                      {row.price !== null ? formatMoney(row.price, row.currency ?? 'KES') : '—'}
                    </td>
                    <td className="px-6 py-3 text-right font-bold text-white">{formatNumber(row.activeSubscriptions)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-800">
          <h4 className="text-sm font-bold text-white">Plan versions</h4>
          <p className="text-[11px] text-slate-500">
            Only active versions are available to new subscribers.
          </p>
        </div>
        {plansError && (
          <div className="p-4">
            <ErrorBanner message={plansError} onRetry={() => void loadPlans()} />
          </div>
        )}
        {loading && plans.length === 0 ? (
          <LoadingState />
        ) : sortedPlans.length === 0 ? (
          !plansError && <EmptyState title="No plans defined" hint="Create the first subscription plan to start selling." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-6 py-3">Plan</th>
                  <th className="px-6 py-3">Price</th>
                  <th className="px-6 py-3">Limits</th>
                  <th className="px-6 py-3">Quality</th>
                  <th className="px-6 py-3">Extras</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3 text-right">Availability</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {sortedPlans.map((plan) => (
                  <tr key={plan.id} className="hover:bg-slate-800/40 transition align-top">
                    <td className="px-6 py-3">
                      <span className="font-semibold text-white block">
                        {plan.name} <span className="text-slate-500 font-medium">v{plan.version}</span>
                      </span>
                      {plan.description && <span className="text-[11px] text-slate-500">{plan.description}</span>}
                    </td>
                    <td className="px-6 py-3 whitespace-nowrap">
                      <span className="font-bold text-white">{formatMoney(plan.price, plan.currency)}</span>
                      <span className="text-slate-500"> / {plan.billingInterval === 'YEARLY' ? 'year' : 'month'}</span>
                    </td>
                    <td className="px-6 py-3 text-slate-400 whitespace-nowrap">
                      {plan.maxProfiles} profiles · {plan.maxRegisteredDevices} devices · {plan.maxConcurrentStreams} streams
                    </td>
                    <td className="px-6 py-3 text-slate-400 whitespace-nowrap">
                      {RESOLUTION_LABELS[plan.maxResolution] ?? plan.maxResolution}
                      {plan.hdrEnabled ? ' · HDR' : ''} · {plan.audioQuality}
                    </td>
                    <td className="px-6 py-3 text-slate-400 whitespace-nowrap">
                      {plan.downloadsEnabled ? `Downloads (${plan.maxDownloadDevices} devices)` : 'No downloads'}
                      {plan.kidsProfilesEnabled ? ' · Kids profiles' : ''}
                    </td>
                    <td className="px-6 py-3">
                      <StatusBadge status={plan.active ? 'ACTIVE' : 'ARCHIVED'} />
                    </td>
                    <td className="px-6 py-3 text-right">
                      <button
                        onClick={() => void handleTogglePlan(plan)}
                        disabled={togglingPlanId !== null}
                        className={`px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-semibold transition disabled:opacity-50 ${
                          plan.active ? 'text-rose-400' : 'text-emerald-400'
                        }`}
                      >
                        {togglingPlanId === plan.id ? 'Saving…' : plan.active ? 'Retire' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <h4 className="text-sm font-bold text-white">
            Subscriptions{' '}
            {subscriptions.length > 0 && (
              <span className="text-slate-500 font-medium">
                ({formatNumber(filteredSubscriptions.length)} of {formatNumber(subscriptions.length)})
              </span>
            )}
          </h4>
          <div className="flex items-center gap-2">
            <div className="relative w-full sm:w-64">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search account or subscription ID..."
                className="w-full pl-9 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              />
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            >
              <option value="ALL">All statuses</option>
              {SUBSCRIPTION_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </div>
        </div>
        {subsError && (
          <div className="p-4">
            <ErrorBanner message={subsError} onRetry={() => void loadSubscriptions()} />
          </div>
        )}
        {loading && subscriptions.length === 0 ? (
          <LoadingState />
        ) : subscriptions.length === 0 ? (
          !subsError && <EmptyState title="No subscriptions yet" />
        ) : filteredSubscriptions.length === 0 ? (
          <EmptyState title="No subscriptions match the current filters" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-6 py-3">Subscription</th>
                  <th className="px-6 py-3">Account</th>
                  <th className="px-6 py-3">Plan</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3">Current Period</th>
                  <th className="px-6 py-3">Entitlements</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {filteredSubscriptions.map((sub) => (
                  <tr key={sub.id} className="hover:bg-slate-800/40 transition">
                    <td className="px-6 py-3 font-mono text-slate-400" title={sub.id}>
                      {shortId(sub.id)}
                    </td>
                    <td className="px-6 py-3 font-mono text-white" title={sub.accountId}>
                      {sub.accountId}
                    </td>
                    <td className="px-6 py-3 whitespace-nowrap">
                      <span className="font-semibold text-white">{sub.plan?.name ?? '—'}</span>
                      {sub.plan && <span className="text-slate-500"> v{sub.plan.version}</span>}
                    </td>
                    <td className="px-6 py-3">
                      <div className="flex flex-col gap-1 items-start">
                        <StatusBadge status={sub.status} />
                        {sub.cancelAtPeriodEnd && (
                          <span className="text-[10px] text-amber-400">Cancels at period end</span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-3 text-slate-400 whitespace-nowrap">
                      {formatDate(sub.currentPeriodStart)} – {formatDate(sub.currentPeriodEnd)}
                    </td>
                    <td className="px-6 py-3 text-slate-400 whitespace-nowrap">
                      {sub.entitlements
                        ? `${sub.entitlements.maxConcurrentStreams} streams · ${sub.entitlements.maxRegisteredDevices} devices · ${
                            RESOLUTION_LABELS[sub.entitlements.maxResolution] ?? sub.entitlements.maxResolution
                          }`
                        : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <Modal isOpen={isCreateOpen} onClose={() => !creating && setIsCreateOpen(false)} title="Create Plan / New Version">
        <form onSubmit={handleCreatePlan} className="space-y-4">
          <p className="text-[11px] text-slate-400 bg-slate-950 border border-slate-800 rounded-lg p-3">
            Plans are versioned. Reusing the name of an existing plan publishes a new version and the server retires the
            previous version so it is no longer offered to new subscribers.
          </p>
          <ErrorBanner message={createError} />

          <div>
            <label className={labelClass}>Plan name *</label>
            <input
              type="text"
              required
              value={form.name}
              onChange={(e) => setField('name', e.target.value)}
              className={inputClass}
              placeholder="e.g. Premium"
            />
            {existingVersion !== null && (
              <p className="mt-1 text-[11px] text-amber-400">
                A plan with this name exists (latest v{existingVersion}). Saving publishes v{existingVersion + 1} and retires
                the current version.
              </p>
            )}
          </div>

          <div>
            <label className={labelClass}>Description</label>
            <textarea
              rows={2}
              value={form.description}
              onChange={(e) => setField('description', e.target.value)}
              className={inputClass}
            />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className={labelClass}>Price *</label>
              <input
                type="number"
                min={0}
                step="0.01"
                required
                value={form.price}
                onChange={(e) => setField('price', e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Currency</label>
              <input
                type="text"
                maxLength={3}
                value={form.currency}
                onChange={(e) => setField('currency', e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Billing interval</label>
              <select
                value={form.billingInterval}
                onChange={(e) => setField('billingInterval', e.target.value as BillingInterval)}
                className={inputClass}
              >
                <option value="MONTHLY">Monthly</option>
                <option value="YEARLY">Yearly</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className={labelClass}>Max profiles</label>
              <input
                type="number"
                min={1}
                step={1}
                required
                value={form.maxProfiles}
                onChange={(e) => setField('maxProfiles', e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Max devices</label>
              <input
                type="number"
                min={1}
                step={1}
                required
                value={form.maxRegisteredDevices}
                onChange={(e) => setField('maxRegisteredDevices', e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Concurrent streams</label>
              <input
                type="number"
                min={1}
                step={1}
                required
                value={form.maxConcurrentStreams}
                onChange={(e) => setField('maxConcurrentStreams', e.target.value)}
                className={inputClass}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Max resolution</label>
              <select
                value={form.maxResolution}
                onChange={(e) => setField('maxResolution', e.target.value as VideoResolution)}
                className={inputClass}
              >
                {(Object.keys(RESOLUTION_LABELS) as VideoResolution[]).map((r) => (
                  <option key={r} value={r}>
                    {RESOLUTION_LABELS[r]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Audio quality</label>
              <input
                type="text"
                list="plan-audio-quality"
                value={form.audioQuality}
                onChange={(e) => setField('audioQuality', e.target.value)}
                className={inputClass}
              />
              <datalist id="plan-audio-quality">
                {AUDIO_SUGGESTIONS.map((a) => (
                  <option key={a} value={a} />
                ))}
              </datalist>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 pt-1">
            <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
              <input
                type="checkbox"
                checked={form.hdrEnabled}
                onChange={(e) => setField('hdrEnabled', e.target.checked)}
                className="rounded border-slate-800 text-red-600 focus:ring-red-500"
              />
              HDR enabled
            </label>
            <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
              <input
                type="checkbox"
                checked={form.kidsProfilesEnabled}
                onChange={(e) => setField('kidsProfilesEnabled', e.target.checked)}
                className="rounded border-slate-800 text-red-600 focus:ring-red-500"
              />
              Kids profiles
            </label>
            <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
              <input
                type="checkbox"
                checked={form.downloadsEnabled}
                onChange={(e) => setField('downloadsEnabled', e.target.checked)}
                className="rounded border-slate-800 text-red-600 focus:ring-red-500"
              />
              Offline downloads
            </label>
            {form.downloadsEnabled && (
              <div>
                <label className={labelClass}>Max download devices</label>
                <input
                  type="number"
                  min={0}
                  step={1}
                  value={form.maxDownloadDevices}
                  onChange={(e) => setField('maxDownloadDevices', e.target.value)}
                  className={inputClass}
                />
              </div>
            )}
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={() => setIsCreateOpen(false)}
              disabled={creating}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={creating}
              className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
            >
              {creating ? 'Publishing…' : existingVersion !== null ? `Publish v${existingVersion + 1}` : 'Create Plan'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
