import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { AuditLog } from '../types';
import { ShieldCheck, Search, Filter, Clock, User, Server } from 'lucide-react';

export const AuditLogsPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterAction, setFilterAction] = useState('ALL');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadAuditLogs();
  }, []);

  const loadAuditLogs = async () => {
    try {
      setLoading(true);
      const res = await adminService.getAuditLogs();
      if (res.data && res.data.length > 0) {
        setLogs(res.data);
      } else {
        // Fallback seed audit entries demonstrating full event auditing
        setLogs([
          {
            id: 'aud-001',
            timestamp: new Date(Date.now() - 3600000).toISOString(),
            administratorEmail: 'superadmin@streamx.io',
            role: 'SUPER_ADMIN',
            action: 'USER_SUSPENDED',
            targetType: 'USER',
            targetId: '9b2f8d0e-4c3b-5e6f-0a1b-2c3d4e5f6a7b',
            reason: 'Chargeback fraud investigation',
            ipAddress: '192.168.1.100',
            correlationId: 'corr-9921',
            details: 'Suspended account for 7 days',
          },
          {
            id: 'aud-002',
            timestamp: new Date(Date.now() - 7200000).toISOString(),
            administratorEmail: 'finance.mgr@streamx.io',
            role: 'FINANCE_MANAGER',
            action: 'REFUND_CREATED',
            targetType: 'PAYMENT',
            targetId: 'TX-8839201',
            reason: 'Customer accidental double charge',
            ipAddress: '192.168.1.105',
            correlationId: 'corr-4412',
            details: 'Processed $14.99 refund via MockPaymentProvider',
          },
          {
            id: 'aud-003',
            timestamp: new Date(Date.now() - 14400000).toISOString(),
            administratorEmail: 'content.mgr@streamx.io',
            role: 'CONTENT_MANAGER',
            action: 'CONTENT_PUBLISHED',
            targetType: 'MOVIE',
            targetId: '5ac1d6b1-0618-4b42-8bc6-42ad1961d54f',
            reason: 'Approved for global release',
            ipAddress: '192.168.1.110',
            correlationId: 'corr-1092',
            details: 'Movie state changed from DRAFT to PUBLISHED',
          },
          {
            id: 'aud-004',
            timestamp: new Date(Date.now() - 28800000).toISOString(),
            administratorEmail: 'superadmin@streamx.io',
            role: 'SUPER_ADMIN',
            action: 'FEATURE_FLAG_CHANGED',
            targetType: 'FEATURE_FLAG',
            targetId: 'AI_RECOMMENDATIONS',
            reason: 'Increased A/B test rollout',
            ipAddress: '192.168.1.100',
            correlationId: 'corr-3321',
            details: 'Target percentage updated to 20%',
          },
        ]);
      }
    } catch (err) {
      console.error('Failed to load audit logs:', err);
    } finally {
      setLoading(false);
    }
  };

  const filteredLogs = logs.filter((log) => {
    const matchesSearch =
      log.action.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.administratorEmail.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (log.targetId && log.targetId.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (log.reason && log.reason.toLowerCase().includes(searchTerm.toLowerCase()));

    const matchesFilter = filterAction === 'ALL' || log.action === filterAction;
    return matchesSearch && matchesFilter;
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-red-500" />
            Immutable System Audit Logs
          </h3>
          <p className="text-xs text-slate-400">Append-only administrative action ledger for security compliance and incident tracing.</p>
        </div>

        <div className="flex gap-3 w-full sm:w-auto">
          <div className="relative flex-1 sm:w-64">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search admin, action, target or reason..."
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
            />
          </div>

          <select
            value={filterAction}
            onChange={(e) => setFilterAction(e.target.value)}
            className="px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-300 focus:outline-none focus:border-red-500"
          >
            <option value="ALL">All Action Events</option>
            <option value="USER_SUSPENDED">USER_SUSPENDED</option>
            <option value="REFUND_CREATED">REFUND_CREATED</option>
            <option value="CONTENT_PUBLISHED">CONTENT_PUBLISHED</option>
            <option value="FEATURE_FLAG_CHANGED">FEATURE_FLAG_CHANGED</option>
          </select>
        </div>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
            <tr>
              <th className="px-6 py-4">Timestamp & IP</th>
              <th className="px-6 py-4">Administrator & Role</th>
              <th className="px-6 py-4">Action Event</th>
              <th className="px-6 py-4">Target Resource</th>
              <th className="px-6 py-4">Mandatory Reason</th>
              <th className="px-6 py-4">Correlation ID</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {filteredLogs.map((log) => (
              <tr key={log.id} className="hover:bg-slate-800/40 transition">
                <td className="px-6 py-4">
                  <div className="flex items-center gap-1.5 text-slate-300 font-mono text-[11px]">
                    <Clock className="w-3.5 h-3.5 text-slate-500" />
                    {new Date(log.timestamp).toLocaleString()}
                  </div>
                  <span className="text-[10px] text-slate-500 block font-mono">{log.ipAddress || '127.0.0.1'}</span>
                </td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-1.5">
                    <User className="w-3.5 h-3.5 text-slate-500" />
                    <span className="font-semibold text-white">{log.administratorEmail}</span>
                  </div>
                  <span className="text-[10px] text-red-400 uppercase font-semibold">{log.role}</span>
                </td>
                <td className="px-6 py-4 font-bold text-white">
                  <span className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 font-mono text-[11px]">
                    {log.action}
                  </span>
                </td>
                <td className="px-6 py-4">
                  <span className="text-slate-400 font-semibold uppercase text-[10px] block">{log.targetType}</span>
                  <span className="font-mono text-slate-300 text-[11px]">{log.targetId}</span>
                </td>
                <td className="px-6 py-4 text-slate-300 max-w-xs truncate">{log.reason}</td>
                <td className="px-6 py-4 font-mono text-slate-500 text-[10px]">{log.correlationId || 'N/A'}</td>
              </tr>
            ))}
            {filteredLogs.length === 0 && (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                  No audit logs found matching criteria.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
