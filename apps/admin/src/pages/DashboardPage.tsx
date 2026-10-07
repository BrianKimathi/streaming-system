import React, { useCallback, useEffect, useState } from 'react';
import {
  Users,
  Clock,
  DollarSign,
  PlayCircle,
  Activity,
  Award,
  Film,
  Smartphone,
  UploadCloud,
  CreditCard,
  AlertTriangle,
} from 'lucide-react';
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';
import { StatCard } from '../components/common/StatCard';
import { ErrorBanner, LoadingState } from '../components/common/Feedback';
import { adminService } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { DashboardSection, PlatformDashboard } from '../types';
import { formatDateTime, formatDuration, formatMoney, formatNumber } from '../utils/format';

const SECTION_LABELS: Record<string, string> = {
  users: 'Accounts (auth-service)',
  catalog: 'Catalog',
  subscriptions: 'Subscriptions',
  billing: 'Billing',
  devices: 'Devices',
  media: 'Media pipeline',
  playback: 'Playback',
  watchHistory: 'Watch history',
  notifications: 'Notifications',
};

const tooltipStyle = { backgroundColor: '#0f172a', borderColor: '#334155', fontSize: 12 };

function shortDate(date: string) {
  const d = new Date(`${date}T00:00:00`);
  return Number.isNaN(d.getTime()) ? date : d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function ChartCard({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h4 className="text-xs font-bold text-white uppercase tracking-wider">{title}</h4>
        <span className="text-xs text-slate-400">{subtitle}</span>
      </div>
      <div className="h-60">{children}</div>
    </div>
  );
}

function Unavailable({ section }: { section: DashboardSection<unknown> | undefined }) {
  return (
    <div className="h-full flex flex-col items-center justify-center text-center gap-1">
      <AlertTriangle className="w-5 h-5 text-amber-500" />
      <p className="text-xs text-slate-400">Data unavailable</p>
      {section?.error && <p className="text-[11px] text-slate-600 max-w-xs truncate">{section.error}</p>}
    </div>
  );
}

export const DashboardPage: React.FC = () => {
  const [dashboard, setDashboard] = useState<PlatformDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDashboard(await adminService.getDashboard());
    } catch (err) {
      setError(errorMessage(err, 'Could not load the platform overview'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading && !dashboard) return <LoadingState label="Collecting live metrics from every service…" />;

  const s = dashboard?.sections;
  const users = s?.users?.available ? s.users.data : undefined;
  const billing = s?.billing?.available ? s.billing.data : undefined;
  const subs = s?.subscriptions?.available ? s.subscriptions.data : undefined;
  const playback = s?.playback?.available ? s.playback.data : undefined;
  const watch = s?.watchHistory?.available ? s.watchHistory.data : undefined;
  const catalog = s?.catalog?.available ? s.catalog.data : undefined;
  const devices = s?.devices?.available ? s.devices.data : undefined;
  const media = s?.media?.available ? s.media.data : undefined;

  const unavailableSections = s
    ? Object.entries(s).filter(([, section]) => !section?.available).map(([key]) => SECTION_LABELS[key] ?? key)
    : [];

  const mrr = subs ? Object.entries(subs.monthlyRecurringRevenueByCurrency ?? {}) : [];
  const currency = billing?.primaryCurrency ?? 'KES';

  return (
    <div className="space-y-6">
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-white mb-1">Platform overview</h3>
          <p className="text-xs text-slate-400">Live figures queried directly from each microservice's database.</p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400">
            Generated: <strong className="text-white">{formatDateTime(dashboard?.generatedAt)}</strong>
          </span>
          <button
            onClick={load}
            disabled={loading}
            className="px-3.5 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </div>

      <ErrorBanner message={error} onRetry={load} />
      {unavailableSections.length > 0 && (
        <ErrorBanner message={`Some services did not respond, so their figures are hidden: ${unavailableSections.join(', ')}.`} />
      )}

      {dashboard && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              title="Registered accounts"
              value={formatNumber(users?.totalAccounts)}
              subtitle={users ? `${formatNumber(users.newAccountsLast7Days)} new in the last 7 days` : undefined}
              unavailable={!users}
              icon={Users}
              iconBgColor="bg-slate-800 text-blue-400"
            />
            <StatCard
              title="Active subscriptions"
              value={formatNumber(subs?.activeSubscriptions)}
              subtitle={subs ? `${formatNumber(subs.newLast30Days)} started in the last 30 days` : undefined}
              unavailable={!subs}
              icon={CreditCard}
              iconBgColor="bg-slate-800 text-purple-400"
            />
            <StatCard
              title="Revenue (completed payments)"
              value={formatMoney(billing?.totalRevenue, currency)}
              subtitle={billing ? `${formatMoney(billing.revenueLast30Days, currency)} in the last 30 days` : undefined}
              unavailable={!billing}
              icon={DollarSign}
              iconBgColor="bg-slate-800 text-emerald-400"
            />
            <StatCard
              title="Monthly recurring revenue"
              value={mrr.length > 0 ? mrr.map(([cur, amount]) => formatMoney(amount, cur)).join(' · ') : formatMoney(0, currency)}
              subtitle="From active subscriptions"
              unavailable={!subs}
              icon={Activity}
              iconBgColor="bg-slate-800 text-indigo-400"
            />
            <StatCard
              title="Streaming now"
              value={formatNumber(playback?.activeStreams)}
              subtitle={playback ? `${formatNumber(playback.sessionsToday)} sessions started today` : undefined}
              unavailable={!playback}
              icon={PlayCircle}
              iconBgColor="bg-slate-800 text-red-400"
            />
            <StatCard
              title="Viewing accounts (24h / 30d)"
              value={playback ? `${formatNumber(playback.dailyActiveAccounts)} / ${formatNumber(playback.monthlyActiveAccounts)}` : '—'}
              subtitle="Distinct accounts that started playback"
              unavailable={!playback}
              icon={Users}
              iconBgColor="bg-slate-800 text-sky-400"
            />
            <StatCard
              title="Watch time (last 7 days)"
              value={formatDuration(watch?.watchSecondsLast7Days)}
              subtitle={watch ? `${formatDuration(watch.totalWatchSeconds)} all time` : undefined}
              unavailable={!watch}
              icon={Clock}
              iconBgColor="bg-slate-800 text-emerald-400"
            />
            <StatCard
              title="Completion rate"
              value={watch ? `${watch.completionRate}%` : '—'}
              subtitle={watch ? `${formatNumber(watch.completedViews)} of ${formatNumber(watch.totalProgressRecords)} titles finished` : undefined}
              unavailable={!watch}
              icon={Award}
              iconBgColor="bg-slate-800 text-amber-400"
            />
            <StatCard
              title="Published titles"
              value={catalog ? formatNumber(catalog.publishedMovies + catalog.publishedTvShows) : '—'}
              subtitle={catalog ? `${formatNumber(catalog.totalMovies)} movies · ${formatNumber(catalog.totalTvShows)} shows in catalog` : undefined}
              unavailable={!catalog}
              icon={Film}
              iconBgColor="bg-slate-800 text-rose-400"
            />
            <StatCard
              title="Active devices"
              value={formatNumber(devices?.activeDevices)}
              subtitle={devices ? `${formatNumber(devices.revokedDevices)} revoked` : undefined}
              unavailable={!devices}
              icon={Smartphone}
              iconBgColor="bg-slate-800 text-teal-400"
            />
            <StatCard
              title="Videos ready to stream"
              value={formatNumber(media?.countByStatus?.COMPLETED)}
              subtitle={media ? `${formatNumber(media.countByStatus?.PROCESSING)} transcoding · ${formatNumber(media.countByStatus?.FAILED)} failed` : undefined}
              unavailable={!media}
              icon={UploadCloud}
              iconBgColor="bg-slate-800 text-orange-400"
            />
            <StatCard
              title="Suspended accounts"
              value={formatNumber(users?.suspendedAccounts)}
              subtitle={users ? `${formatNumber(users.newAccountsToday)} sign-ups today` : undefined}
              unavailable={!users}
              icon={AlertTriangle}
              iconBgColor="bg-slate-800 text-rose-400"
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <ChartCard title="New sign-ups" subtitle="Last 14 days">
              {users ? (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={users.dailySignups}>
                    <CartesianGrid stroke="#1e293b" vertical={false} />
                    <XAxis dataKey="date" tickFormatter={shortDate} stroke="#64748b" fontSize={11} />
                    <YAxis allowDecimals={false} stroke="#64748b" fontSize={11} />
                    <Tooltip contentStyle={tooltipStyle} labelFormatter={(d) => shortDate(String(d))} />
                    <Bar dataKey="count" name="Sign-ups" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <Unavailable section={s?.users} />
              )}
            </ChartCard>

            <ChartCard title="Playback sessions" subtitle="Last 14 days">
              {playback ? (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={playback.dailySessions}>
                    <CartesianGrid stroke="#1e293b" vertical={false} />
                    <XAxis dataKey="date" tickFormatter={shortDate} stroke="#64748b" fontSize={11} />
                    <YAxis allowDecimals={false} stroke="#64748b" fontSize={11} />
                    <Tooltip contentStyle={tooltipStyle} labelFormatter={(d) => shortDate(String(d))} />
                    <Area type="monotone" dataKey="count" name="Sessions" stroke="#dc2626" fill="#1e293b" fillOpacity={0.6} />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <Unavailable section={s?.playback} />
              )}
            </ChartCard>

            <ChartCard title="Revenue" subtitle={`Last 14 days · ${currency}`}>
              {billing ? (
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={billing.dailyRevenue}>
                    <CartesianGrid stroke="#1e293b" vertical={false} />
                    <XAxis dataKey="date" tickFormatter={shortDate} stroke="#64748b" fontSize={11} />
                    <YAxis stroke="#64748b" fontSize={11} />
                    <Tooltip
                      contentStyle={tooltipStyle}
                      labelFormatter={(d) => shortDate(String(d))}
                      formatter={(value) => formatMoney(Number(value), currency)}
                    />
                    <Area type="monotone" dataKey="amount" name="Revenue" stroke="#10b981" fill="#1e293b" fillOpacity={0.6} />
                  </AreaChart>
                </ResponsiveContainer>
              ) : (
                <Unavailable section={s?.billing} />
              )}
            </ChartCard>
          </div>

          {subs && subs.activeByPlan.length > 0 && (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
              <h4 className="text-xs font-bold text-white uppercase tracking-wider mb-4">Active subscribers by plan</h4>
              <table className="w-full text-xs">
                <thead className="text-slate-400 text-left">
                  <tr>
                    <th className="py-2">Plan</th>
                    <th className="py-2">Price</th>
                    <th className="py-2 text-right">Active subscribers</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {subs.activeByPlan.map((row) => (
                    <tr key={row.planId}>
                      <td className="py-2 text-white">
                        {row.planName} {row.planVersion ? <span className="text-slate-500">v{row.planVersion}</span> : null}
                      </td>
                      <td className="py-2 text-slate-300">{formatMoney(row.price, row.currency ?? currency)}</td>
                      <td className="py-2 text-right text-white font-semibold">{formatNumber(row.activeSubscriptions)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
};
