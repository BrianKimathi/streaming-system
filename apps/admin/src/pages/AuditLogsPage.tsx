import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ShieldCheck, Search, Clock, User, RefreshCw, Download, ChevronDown, ChevronRight } from 'lucide-react';
import { adminService } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { AuditLog } from '../types';
import { EmptyState, ErrorBanner, LoadingState } from '../components/common/Feedback';
import { formatDateTime, humanizeRole } from '../utils/format';

const CSV_COLUMNS: (keyof AuditLog)[] = [
  'timestamp',
  'administratorEmail',
  'administratorId',
  'role',
  'action',
  'targetType',
  'targetId',
  'reason',
  'details',
  'ipAddress',
  'correlationId',
  'id',
];

function csvCell(value: unknown): string {
  if (value === null || value === undefined) return '';
  const text = String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function exportCsv(rows: AuditLog[]) {
  const lines = [CSV_COLUMNS.join(','), ...rows.map((row) => CSV_COLUMNS.map((col) => csvCell(row[col])).join(','))];
  const blob = new Blob([lines.join('\r\n')], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `audit-logs-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

const DetailRow: React.FC<{ label: string; value: string | null | undefined; mono?: boolean }> = ({ label, value, mono }) => (
  <div>
    <dt className="text-[10px] uppercase text-slate-500 font-semibold">{label}</dt>
    <dd className={`text-slate-300 break-all ${mono ? 'font-mono text-[11px]' : ''}`}>{value || '—'}</dd>
  </div>
);

export const AuditLogsPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [targetTypeFilter, setTargetTypeFilter] = useState('ALL');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setLogs(await adminService.getAuditLogs());
    } catch (err) {
      setError(errorMessage(err, 'Failed to load audit logs'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const targetTypes = useMemo(() => Array.from(new Set(logs.map((l) => l.targetType))).sort(), [logs]);

  const filteredLogs = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return logs.filter((log) => {
      if (targetTypeFilter !== 'ALL' && log.targetType !== targetTypeFilter) return false;
      if (!term) return true;
      return [log.action, log.administratorEmail, log.targetType, log.targetId, log.reason, log.details]
        .filter((v): v is string => typeof v === 'string')
        .some((v) => v.toLowerCase().includes(term));
    });
  }, [logs, searchTerm, targetTypeFilter]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-red-500" />
            Audit Logs
          </h3>
          <p className="text-xs text-slate-400">The 50 most recent administrative actions recorded by the admin service.</p>
        </div>

        <div className="flex flex-wrap gap-2 w-full lg:w-auto">
          <div className="relative flex-1 lg:w-64">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search action, admin or target..."
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
            />
          </div>
          <select
            value={targetTypeFilter}
            onChange={(e) => setTargetTypeFilter(e.target.value)}
            className="px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-300 focus:outline-none focus:border-red-500"
          >
            <option value="ALL">All target types</option>
            {targetTypes.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
          <button
            onClick={() => void load()}
            disabled={loading}
            className="px-3 py-2 bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-lg text-xs font-semibold text-slate-300 flex items-center gap-1.5 disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={() => exportCsv(filteredLogs)}
            disabled={filteredLogs.length === 0}
            className="px-3 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Download className="w-3.5 h-3.5" />
            Export CSV
          </button>
        </div>
      </div>

      {error ? (
        <ErrorBanner message={error} onRetry={() => void load()} />
      ) : loading && logs.length === 0 ? (
        <LoadingState label="Loading audit logs…" />
      ) : logs.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl">
          <EmptyState
            title="No audit entries yet"
            hint="Administrative actions such as account blocks, feature flag changes, incidents and ticket updates will appear here."
          />
        </div>
      ) : (
        <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="px-4 py-4 w-8"></th>
                <th className="px-4 py-4">Timestamp</th>
                <th className="px-4 py-4">Administrator</th>
                <th className="px-4 py-4">Action</th>
                <th className="px-4 py-4">Target</th>
                <th className="px-4 py-4">Reason</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {filteredLogs.map((log) => {
                const expanded = expandedId === log.id;
                return (
                  <React.Fragment key={log.id}>
                    <tr
                      onClick={() => setExpandedId(expanded ? null : log.id)}
                      className={`cursor-pointer transition ${expanded ? 'bg-slate-800/40' : 'hover:bg-slate-800/40'}`}
                    >
                      <td className="px-4 py-4 text-slate-500">
                        {expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                      </td>
                      <td className="px-4 py-4">
                        <div className="flex items-center gap-1.5 text-slate-300 font-mono text-[11px] whitespace-nowrap">
                          <Clock className="w-3.5 h-3.5 text-slate-500" />
                          {formatDateTime(log.timestamp)}
                        </div>
                      </td>
                      <td className="px-4 py-4">
                        <div className="flex items-center gap-1.5">
                          <User className="w-3.5 h-3.5 text-slate-500" />
                          <span className="font-semibold text-white">{log.administratorEmail}</span>
                        </div>
                        {log.role && (
                          <span className="text-[10px] text-red-400 uppercase font-semibold">{humanizeRole(log.role)}</span>
                        )}
                      </td>
                      <td className="px-4 py-4">
                        <span className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 font-mono text-[11px] font-bold text-white">
                          {log.action}
                        </span>
                      </td>
                      <td className="px-4 py-4">
                        <span className="text-slate-400 font-semibold uppercase text-[10px] block">{log.targetType}</span>
                        <span className="font-mono text-slate-300 text-[11px] break-all">{log.targetId ?? '—'}</span>
                      </td>
                      <td className="px-4 py-4 text-slate-300 max-w-xs truncate">{log.reason ?? <span className="text-slate-600">—</span>}</td>
                    </tr>
                    {expanded && (
                      <tr className="bg-slate-950/60">
                        <td></td>
                        <td colSpan={5} className="px-4 py-4">
                          <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 text-xs">
                            <DetailRow label="Details" value={log.details} />
                            <DetailRow label="Reason" value={log.reason} />
                            <DetailRow label="Target ID" value={log.targetId} mono />
                            <DetailRow label="Administrator ID" value={log.administratorId} mono />
                            <DetailRow label="IP address" value={log.ipAddress} mono />
                            <DetailRow label="Correlation ID" value={log.correlationId} mono />
                            <DetailRow label="Audit entry ID" value={log.id} mono />
                          </dl>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
              {filteredLogs.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                    No audit entries match the current search or filter.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
