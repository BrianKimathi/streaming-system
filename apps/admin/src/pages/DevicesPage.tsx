import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Ban,
  CheckCircle2,
  Gamepad2,
  Laptop,
  LucideIcon,
  MonitorSmartphone,
  RefreshCw,
  Search,
  ShieldAlert,
  Smartphone,
  Tablet,
  Tv,
  X,
} from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { DeviceRegistration, DeviceStats } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatCard } from '../components/common/StatCard';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDateTime, formatNumber, shortId } from '../utils/format';

const DEVICE_TYPES = ['TV', 'PHONE', 'TABLET', 'LAPTOP', 'CONSOLE'];

const DEVICE_ICONS: Record<string, LucideIcon> = {
  TV: Tv,
  PHONE: Smartphone,
  TABLET: Tablet,
  LAPTOP: Laptop,
  CONSOLE: Gamepad2,
};

const deviceIcon = (type: string | null | undefined): LucideIcon =>
  DEVICE_ICONS[(type ?? '').toUpperCase()] ?? MonitorSmartphone;

export const DevicesPage: React.FC = () => {
  const [stats, setStats] = useState<DeviceStats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);

  const [devices, setDevices] = useState<DeviceRegistration[]>([]);
  const [devicesError, setDevicesError] = useState<string | null>(null);
  const [devicesLoading, setDevicesLoading] = useState(true);

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [typeFilter, setTypeFilter] = useState<string>('ALL');
  const [accountInput, setAccountInput] = useState('');
  const [appliedAccount, setAppliedAccount] = useState('');

  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      setStats(await adminService.getDeviceStats());
      setStatsError(null);
    } catch (err) {
      setStatsError(errorMessage(err, 'Failed to load device stats'));
    } finally {
      setStatsLoading(false);
    }
  }, []);

  const loadDevices = useCallback(async (accountId: string) => {
    setDevicesLoading(true);
    try {
      setDevices(await adminService.getDevices(accountId || undefined));
      setDevicesError(null);
    } catch (err) {
      setDevices([]);
      setDevicesError(errorMessage(err, 'Failed to load registered devices'));
    } finally {
      setDevicesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  useEffect(() => {
    void loadDevices(appliedAccount);
  }, [appliedAccount, loadDevices]);

  const refreshAll = () => {
    void loadStats();
    void loadDevices(appliedAccount);
  };

  const applyAccountFilter = (e: React.FormEvent) => {
    e.preventDefault();
    setAppliedAccount(accountInput.trim());
  };

  const clearAccountFilter = () => {
    setAccountInput('');
    setAppliedAccount('');
  };

  const typeOptions = useMemo(() => {
    const seen = new Set(DEVICE_TYPES);
    devices.forEach((d) => d.deviceType && seen.add(d.deviceType.toUpperCase()));
    return Array.from(seen);
  }, [devices]);

  const filtered = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return devices.filter(
      (d) =>
        (statusFilter === 'ALL' || d.status === statusFilter) &&
        (typeFilter === 'ALL' || (d.deviceType ?? '').toUpperCase() === typeFilter) &&
        (!term ||
          (d.deviceName ?? '').toLowerCase().includes(term) ||
          d.deviceFingerprint.toLowerCase().includes(term) ||
          d.accountId.toLowerCase().includes(term))
    );
  }, [devices, searchTerm, statusFilter, typeFilter]);

  const activeByType = Object.entries(stats?.activeDevicesByType ?? {}).sort((a, b) => b[1] - a[1]);

  const handleRevoke = async (device: DeviceRegistration) => {
    const label = device.deviceName || device.deviceFingerprint;
    if (!window.confirm(`Revoke "${label}" for account ${device.accountId}? The device will lose access immediately.`)) {
      return;
    }
    setRevokingId(device.id);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.revokeDevice(device.id);
      setDevices((prev) => prev.map((d) => (d.id === updated.id ? updated : d)));
      setActionSuccess(`Device "${updated.deviceName || updated.deviceFingerprint}" is now ${updated.status}.`);
      audit({
        action: 'DEVICE_REVOKED',
        targetType: 'DEVICE',
        targetId: updated.id,
        details: `${updated.deviceName || updated.deviceFingerprint} (account ${updated.accountId})`,
      });
      void loadStats();
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to revoke device'));
    } finally {
      setRevokingId(null);
    }
  };

  const statsUnavailable = !stats && !statsLoading;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">Registered Device Management</h3>
          <p className="text-xs text-slate-400">Inspect registered devices per account and revoke access when needed.</p>
        </div>
        <button
          onClick={refreshAll}
          disabled={statsLoading || devicesLoading}
          className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${statsLoading || devicesLoading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <ErrorBanner message={actionError} />
      <SuccessBanner message={actionSuccess} />
      <ErrorBanner message={statsError} onRetry={() => void loadStats()} />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          title="Total Devices"
          value={formatNumber(stats?.totalDevices)}
          icon={MonitorSmartphone}
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Active Devices"
          value={formatNumber(stats?.activeDevices)}
          icon={CheckCircle2}
          iconBgColor="bg-emerald-500/10 text-emerald-400"
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Revoked Devices"
          value={formatNumber(stats?.revokedDevices)}
          icon={Ban}
          iconBgColor="bg-rose-500/10 text-rose-400"
          unavailable={statsUnavailable}
        />
      </div>

      {stats && (
        <section className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <h4 className="text-sm font-bold text-white mb-3">Active devices by type</h4>
          {activeByType.length === 0 ? (
            <p className="text-xs text-slate-500">No active devices.</p>
          ) : (
            <div className="flex flex-wrap gap-3">
              {activeByType.map(([type, count]) => {
                const Icon = deviceIcon(type);
                return (
                  <div
                    key={type}
                    className="flex items-center gap-2 px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs"
                  >
                    <Icon className="w-4 h-4 text-slate-400" />
                    <span className="text-slate-300 font-medium">{type}</span>
                    <span className="text-white font-bold">{formatNumber(count)}</span>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      )}

      <section className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-800 flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <h4 className="text-sm font-bold text-white">
            Devices{' '}
            {devices.length > 0 && (
              <span className="text-slate-500 font-medium">
                ({formatNumber(filtered.length)} of {formatNumber(devices.length)})
              </span>
            )}
          </h4>
          <div className="flex flex-col sm:flex-row gap-2">
            <form onSubmit={applyAccountFilter} className="flex items-center gap-1">
              <input
                type="text"
                value={accountInput}
                onChange={(e) => setAccountInput(e.target.value)}
                placeholder="Filter by account ID (server)"
                className="w-full sm:w-56 px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              />
              <button
                type="submit"
                disabled={devicesLoading}
                className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
              >
                Apply
              </button>
              {appliedAccount && (
                <button
                  type="button"
                  onClick={clearAccountFilter}
                  title="Clear account filter"
                  className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg transition"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </form>
            <div className="relative sm:w-60">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search name, fingerprint, account..."
                className="w-full pl-9 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              />
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            >
              <option value="ALL">All statuses</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="REVOKED">REVOKED</option>
            </select>
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            >
              <option value="ALL">All types</option>
              {typeOptions.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
        </div>
        {appliedAccount && (
          <div className="px-6 py-2 text-[11px] text-slate-400 border-b border-slate-800 bg-slate-950/50">
            Showing devices for account <span className="font-mono text-white">{appliedAccount}</span>
          </div>
        )}
        {devicesError && (
          <div className="p-4">
            <ErrorBanner message={devicesError} onRetry={() => void loadDevices(appliedAccount)} />
          </div>
        )}
        {devicesLoading ? (
          <LoadingState />
        ) : devices.length === 0 ? (
          !devicesError && (
            <EmptyState title={appliedAccount ? 'No devices registered for this account' : 'No devices registered'} />
          )
        ) : filtered.length === 0 ? (
          <EmptyState title="No devices match the current filters" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-6 py-3">Device</th>
                  <th className="px-6 py-3">Fingerprint</th>
                  <th className="px-6 py-3">Account</th>
                  <th className="px-6 py-3">Platform / App</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3">Registered</th>
                  <th className="px-6 py-3">Last Seen</th>
                  <th className="px-6 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {filtered.map((device) => {
                  const Icon = deviceIcon(device.deviceType);
                  return (
                    <tr key={device.id} className="hover:bg-slate-800/40 transition">
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-2">
                          <Icon className="w-4 h-4 text-slate-400 shrink-0" />
                          <div>
                            <span className="font-semibold text-white block">{device.deviceName || 'Unnamed device'}</span>
                            <span className="text-[10px] text-slate-500">{device.deviceType}</span>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-3 font-mono text-slate-400 text-[11px]" title={device.deviceFingerprint}>
                        {device.deviceFingerprint.length > 20
                          ? `${device.deviceFingerprint.slice(0, 20)}…`
                          : device.deviceFingerprint}
                      </td>
                      <td className="px-6 py-3 font-mono text-slate-400 text-[11px]" title={device.accountId}>
                        {shortId(device.accountId)}
                      </td>
                      <td className="px-6 py-3 text-slate-400">
                        {device.platform ?? '—'}
                        {device.appVersion && <span className="text-slate-500"> · v{device.appVersion}</span>}
                      </td>
                      <td className="px-6 py-3">
                        <StatusBadge status={device.status} />
                      </td>
                      <td className="px-6 py-3 text-slate-400 whitespace-nowrap">{formatDateTime(device.registeredAt)}</td>
                      <td className="px-6 py-3 text-slate-400 whitespace-nowrap">{formatDateTime(device.lastSeenAt)}</td>
                      <td className="px-6 py-3 text-right">
                        {device.status === 'ACTIVE' && (
                          <button
                            onClick={() => void handleRevoke(device)}
                            disabled={revokingId !== null}
                            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-rose-400 text-xs font-semibold flex items-center gap-1.5 ml-auto transition disabled:opacity-50"
                          >
                            <ShieldAlert className="w-3.5 h-3.5" />
                            {revokingId === device.id ? 'Revoking…' : 'Revoke'}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
};
