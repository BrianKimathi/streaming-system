import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Server,
  Database,
  HardDrive,
  AlertTriangle,
  CheckCircle,
  RefreshCw,
  Plus,
  Loader2,
  Siren,
  Boxes,
} from 'lucide-react';
import { adminService } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { ComponentHealth, Incident, IncidentSeverity, IncidentStatus, SystemHealth } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { Modal } from '../components/common/Modal';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDateTime, formatNumber } from '../utils/format';

const AUTO_REFRESH_MS = 30_000;
const SEVERITIES: IncidentSeverity[] = ['SEV1', 'SEV2', 'SEV3', 'SEV4'];
const INCIDENT_STATUSES: IncidentStatus[] = ['OPEN', 'INVESTIGATING', 'MITIGATED', 'RESOLVED', 'CLOSED'];

const SEVERITY_COLORS: Record<IncidentSeverity, string> = {
  SEV1: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  SEV2: 'bg-orange-500/15 text-orange-300 border-orange-500/30',
  SEV3: 'bg-amber-500/10 text-amber-300 border-amber-500/20',
  SEV4: 'bg-slate-800 text-slate-300 border-slate-700',
};

const inputClass =
  'w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500';
const selectClass =
  'px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-300 focus:outline-none focus:border-red-500';

const ServiceCard: React.FC<{ component: ComponentHealth }> = ({ component }) => {
  const up = component.status === 'UP';
  return (
    <div
      className={`bg-slate-950 border p-3.5 rounded-lg space-y-1.5 ${up ? 'border-slate-800' : 'border-rose-500/40'}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-xs font-bold text-white truncate">{component.name}</span>
        <StatusBadge status={component.status} />
      </div>
      <div className="flex items-center gap-3 text-[11px] text-slate-400">
        <span>Latency: {component.latencyMs !== undefined ? `${component.latencyMs} ms` : '—'}</span>
        {component.httpStatus !== undefined && <span>HTTP {component.httpStatus}</span>}
      </div>
      {component.error && <p className="text-[11px] text-rose-300 break-words">{component.error}</p>}
    </div>
  );
};

const InfrastructureCard: React.FC<{ component: ComponentHealth }> = ({ component }) => {
  const up = component.status === 'UP';
  const isPostgres = component.name.toLowerCase().includes('postgres');
  const Icon = isPostgres ? Database : HardDrive;
  const hasConnections = component.connections !== undefined && component.maxConnections !== undefined;
  const usage =
    hasConnections && component.maxConnections
      ? Math.min(100, Math.round(((component.connections ?? 0) / component.maxConnections) * 100))
      : null;

  return (
    <div className={`bg-slate-900 border rounded-xl p-5 space-y-3 ${up ? 'border-slate-800' : 'border-rose-500/40'}`}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
          <Icon className={`w-4 h-4 ${isPostgres ? 'text-blue-400' : 'text-emerald-400'}`} />
          {component.name}
        </span>
        <StatusBadge status={component.status} />
      </div>

      <div className="grid grid-cols-2 gap-2 text-[11px] text-slate-400">
        <span>Latency: {component.latencyMs !== undefined ? `${component.latencyMs} ms` : '—'}</span>
        {component.version && <span>Version: {component.version}</span>}
      </div>

      {hasConnections && (
        <div className="space-y-1">
          <div className="flex justify-between text-[11px] text-slate-400">
            <span>Connections</span>
            <span className="text-white font-semibold">
              {formatNumber(component.connections)} / {formatNumber(component.maxConnections)}
              {usage !== null && <span className="text-slate-500 font-normal"> ({usage}%)</span>}
            </span>
          </div>
          {usage !== null && (
            <div className="h-1.5 bg-slate-950 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full ${usage >= 85 ? 'bg-rose-500' : usage >= 60 ? 'bg-amber-500' : 'bg-emerald-500'}`}
                style={{ width: `${usage}%` }}
              />
            </div>
          )}
        </div>
      )}

      {component.error && <p className="text-[11px] text-rose-300 break-words">{component.error}</p>}
    </div>
  );
};

interface IncidentForm {
  title: string;
  description: string;
  severity: IncidentSeverity;
  affectedServices: string;
}

const emptyIncidentForm: IncidentForm = { title: '', description: '', severity: 'SEV3', affectedServices: '' };

export const OperationsHealthPage: React.FC = () => {
  const [health, setHealth] = useState<SystemHealth | null>(null);
  const [healthLoading, setHealthLoading] = useState(true);
  const [healthError, setHealthError] = useState<string | null>(null);
  const healthInFlight = useRef(false);

  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [incidentsLoading, setIncidentsLoading] = useState(true);
  const [incidentsError, setIncidentsError] = useState<string | null>(null);

  const [pendingIncidentId, setPendingIncidentId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<IncidentForm>(emptyIncidentForm);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const loadHealth = useCallback(async () => {
    if (healthInFlight.current) return;
    healthInFlight.current = true;
    setHealthLoading(true);
    try {
      setHealth(await adminService.getSystemHealth());
      setHealthError(null);
    } catch (err) {
      setHealthError(errorMessage(err, 'Failed to load system health'));
    } finally {
      healthInFlight.current = false;
      setHealthLoading(false);
    }
  }, []);

  const loadIncidents = useCallback(async () => {
    setIncidentsLoading(true);
    setIncidentsError(null);
    try {
      setIncidents(await adminService.getIncidents());
    } catch (err) {
      setIncidentsError(errorMessage(err, 'Failed to load incidents'));
    } finally {
      setIncidentsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadHealth();
    void loadIncidents();
    const interval = window.setInterval(() => void loadHealth(), AUTO_REFRESH_MS);
    return () => window.clearInterval(interval);
  }, [loadHealth, loadIncidents]);

  const handleStatusChange = async (incident: Incident, status: IncidentStatus) => {
    if (status === incident.status) return;
    setPendingIncidentId(incident.id);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.updateIncidentStatus(incident.id, status);
      setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
      setActionSuccess(`Incident "${updated.title}" is now ${updated.status}.`);
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to update incident status'));
    } finally {
      setPendingIncidentId(null);
    }
  };

  const openCreate = () => {
    setForm(emptyIncidentForm);
    setCreateError(null);
    setShowCreate(true);
  };

  const toggleAffectedService = (name: string) => {
    const current = form.affectedServices
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const next = current.includes(name) ? current.filter((s) => s !== name) : [...current, name];
    setForm({ ...form, affectedServices: next.join(', ') });
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) {
      setCreateError('Title is required.');
      return;
    }
    setCreating(true);
    setCreateError(null);
    try {
      const created = await adminService.createIncident(
        form.title.trim(),
        form.description.trim(),
        form.severity,
        form.affectedServices.trim()
      );
      setIncidents((prev) => [created, ...prev.filter((i) => i.id !== created.id)]);
      setShowCreate(false);
      setActionError(null);
      setActionSuccess(`Incident "${created.title}" opened as ${created.severity}.`);
    } catch (err) {
      setCreateError(errorMessage(err, 'Failed to create incident'));
    } finally {
      setCreating(false);
    }
  };

  const selectedServices = form.affectedServices
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const degraded = health?.status !== 'UP';

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <Server className="w-5 h-5 text-red-500" />
            Operations & System Health
          </h3>
          <p className="text-xs text-slate-400">
            Live probes of every service and infrastructure component, refreshed every {AUTO_REFRESH_MS / 1000}s.
          </p>
        </div>
        <button
          onClick={() => void loadHealth()}
          disabled={healthLoading}
          className="px-3 py-2 bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-lg text-xs font-semibold text-slate-300 flex items-center gap-1.5 disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${healthLoading ? 'animate-spin' : ''}`} />
          Refresh now
        </button>
      </div>

      {healthError && (
        <ErrorBanner
          message={health ? `${healthError} — showing results from ${formatDateTime(health.checkedAt)}.` : healthError}
          onRetry={() => void loadHealth()}
        />
      )}

      {!health ? (
        healthLoading ? <LoadingState label="Probing platform components…" /> : null
      ) : (
        <>
          <div
            className={`border rounded-xl p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
              degraded ? 'bg-rose-500/10 border-rose-500/30' : 'bg-emerald-500/10 border-emerald-500/20'
            }`}
          >
            <div className="flex items-center gap-3">
              {degraded ? (
                <AlertTriangle className="w-6 h-6 text-rose-400" />
              ) : (
                <CheckCircle className="w-6 h-6 text-emerald-400" />
              )}
              <div>
                <h4 className="font-bold text-white text-sm">
                  Platform status:{' '}
                  <span className={degraded ? 'text-rose-300' : 'text-emerald-300'}>
                    {degraded ? 'DEGRADED' : 'ALL SYSTEMS OPERATIONAL'}
                  </span>
                </h4>
                <p className="text-xs text-slate-400">
                  {health.componentsDown === 0
                    ? `${health.services.length + health.infrastructure.length} components responding.`
                    : `${health.componentsDown} of ${health.services.length + health.infrastructure.length} components down.`}
                </p>
              </div>
            </div>
            <span className="text-[11px] text-slate-400">Checked {formatDateTime(health.checkedAt)}</span>
          </div>

          <div className="space-y-3">
            <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
              <Database className="w-4 h-4 text-blue-400" />
              Infrastructure
            </h4>
            {health.infrastructure.length === 0 ? (
              <div className="bg-slate-900 border border-slate-800 rounded-xl">
                <EmptyState title="No infrastructure probes reported" />
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {health.infrastructure.map((c) => (
                  <InfrastructureCard key={c.name} component={c} />
                ))}
              </div>
            )}
          </div>

          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
            <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
              <Boxes className="w-4 h-4 text-red-500" />
              Services ({health.services.filter((s) => s.status === 'UP').length} / {health.services.length} up)
            </h4>
            {health.services.length === 0 ? (
              <EmptyState
                title="No services configured for probing"
                hint="The admin service probes the services listed in its health.services configuration."
              />
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {health.services.map((s) => (
                  <ServiceCard key={s.name} component={s} />
                ))}
              </div>
            )}
          </div>
        </>
      )}

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between gap-3">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <Siren className="w-4 h-4 text-red-500" />
            Incidents
          </h4>
          <div className="flex gap-2">
            <button
              onClick={() => void loadIncidents()}
              disabled={incidentsLoading}
              className="px-3 py-1.5 bg-slate-950 border border-slate-800 hover:border-slate-700 rounded-lg text-xs font-semibold text-slate-300 flex items-center gap-1.5 disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${incidentsLoading ? 'animate-spin' : ''}`} />
              Refresh
            </button>
            <button
              onClick={openCreate}
              className="px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition"
            >
              <Plus className="w-3.5 h-3.5" />
              Declare incident
            </button>
          </div>
        </div>

        <ErrorBanner message={actionError} />
        <SuccessBanner message={actionSuccess} />

        {incidentsError ? (
          <ErrorBanner message={incidentsError} onRetry={() => void loadIncidents()} />
        ) : incidentsLoading && incidents.length === 0 ? (
          <LoadingState label="Loading incidents…" />
        ) : incidents.length === 0 ? (
          <EmptyState title="No incidents recorded" hint="Declared incidents and their status history will appear here." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-4 py-3">Incident</th>
                  <th className="px-4 py-3">Severity</th>
                  <th className="px-4 py-3">Affected services</th>
                  <th className="px-4 py-3">Opened</th>
                  <th className="px-4 py-3">Resolved</th>
                  <th className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {incidents.map((incident) => (
                  <tr key={incident.id} className="hover:bg-slate-800/40 transition align-top">
                    <td className="px-4 py-3 max-w-sm">
                      <span className="font-semibold text-white block">{incident.title}</span>
                      {incident.description && (
                        <span className="text-[11px] text-slate-400 block whitespace-pre-wrap">{incident.description}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`px-2 py-0.5 rounded border text-[10px] font-bold ${SEVERITY_COLORS[incident.severity] ?? SEVERITY_COLORS.SEV4}`}
                      >
                        {incident.severity}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-400">{incident.affectedServices || '—'}</td>
                    <td className="px-4 py-3 text-slate-400">
                      {formatDateTime(incident.createdAt)}
                      {incident.createdBy && <span className="block text-[10px] text-slate-500">by {incident.createdBy}</span>}
                    </td>
                    <td className="px-4 py-3 text-slate-400">{formatDateTime(incident.resolvedAt)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <select
                          value={incident.status}
                          disabled={pendingIncidentId !== null}
                          onChange={(e) => void handleStatusChange(incident, e.target.value as IncidentStatus)}
                          className={`${selectClass} disabled:opacity-50`}
                        >
                          {INCIDENT_STATUSES.map((s) => (
                            <option key={s} value={s}>
                              {s}
                            </option>
                          ))}
                        </select>
                        {pendingIncidentId === incident.id && <Loader2 className="w-4 h-4 animate-spin text-red-500" />}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal isOpen={showCreate} onClose={() => !creating && setShowCreate(false)} title="Declare incident">
        <form onSubmit={(e) => void handleCreate(e)} className="space-y-4">
          <ErrorBanner message={createError} />
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Title</label>
            <input
              required
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className={inputClass}
            />
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
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Severity</label>
            <select
              value={form.severity}
              onChange={(e) => setForm({ ...form, severity: e.target.value as IncidentSeverity })}
              className={`${selectClass} w-full`}
            >
              {SEVERITIES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Affected services</label>
            <input
              value={form.affectedServices}
              onChange={(e) => setForm({ ...form, affectedServices: e.target.value })}
              placeholder="Comma-separated, e.g. billing-service, api-gateway"
              className={inputClass}
            />
            {health && health.services.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-2">
                {[...health.services, ...health.infrastructure].map((c) => {
                  const selected = selectedServices.includes(c.name);
                  return (
                    <button
                      key={c.name}
                      type="button"
                      onClick={() => toggleAffectedService(c.name)}
                      className={`px-2 py-0.5 rounded border text-[10px] font-mono transition ${
                        selected
                          ? 'bg-red-600/20 border-red-500/40 text-red-200'
                          : 'bg-slate-950 border-slate-800 text-slate-400 hover:border-slate-700'
                      } ${c.status === 'DOWN' ? 'ring-1 ring-rose-500/40' : ''}`}
                    >
                      {c.name}
                    </button>
                  );
                })}
              </div>
            )}
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
              Declare incident
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
