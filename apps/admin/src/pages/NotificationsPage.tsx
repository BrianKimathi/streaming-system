import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Bell,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clock,
  Mail,
  MessageSquare,
  RefreshCw,
  Send,
  X,
} from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type {
  NotificationChannel,
  NotificationLog,
  NotificationStats,
  SendNotificationRequest,
  UserAccount,
} from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatCard } from '../components/common/StatCard';
import { Modal } from '../components/common/Modal';
import { EmptyState, ErrorBanner, LoadingState } from '../components/common/Feedback';
import { formatDateTime, formatNumber, shortId } from '../utils/format';

const CHANNELS: NotificationChannel[] = ['EMAIL', 'SMS', 'IN_APP'];
const LOG_STATUSES = ['SENT', 'DELIVERED', 'FAILED', 'PENDING'];

const inputClass =
  'w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500';
const labelClass = 'block text-xs font-semibold text-slate-300 mb-1';

const recipientFor = (account: UserAccount | undefined, channel: NotificationChannel): string =>
  (channel === 'SMS' ? account?.phoneNumber : account?.email) ?? '';

const accountLabel = (account: UserAccount) =>
  [account.email, account.phoneNumber].filter(Boolean).join(' · ') || account.id;

const ChannelIcon: React.FC<{ channel: string }> = ({ channel }) => {
  if (channel === 'SMS') return <MessageSquare className="w-3.5 h-3.5 text-slate-400" />;
  if (channel === 'IN_APP') return <Bell className="w-3.5 h-3.5 text-slate-400" />;
  return <Mail className="w-3.5 h-3.5 text-slate-400" />;
};

const SendResult: React.FC<{ log: NotificationLog }> = ({ log }) => {
  const status = (log.status ?? '').toUpperCase();
  if (status === 'SENT' || status === 'DELIVERED') {
    return (
      <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 p-3 rounded-lg text-xs font-medium flex items-start gap-2">
        <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
        <span>
          Server reported <strong>{status}</strong> via {log.channel} to {log.recipient}.
        </span>
      </div>
    );
  }
  if (status === 'FAILED') {
    return (
      <div className="bg-rose-500/10 border border-rose-500/20 text-rose-300 p-3 rounded-lg text-xs font-medium flex items-start gap-2">
        <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
        <span>
          Delivery <strong>FAILED</strong> via {log.channel} to {log.recipient}
          {log.failureReason ? `: ${log.failureReason}` : ' (no failure reason returned).'}
        </span>
      </div>
    );
  }
  return (
    <div className="bg-amber-500/10 border border-amber-500/20 text-amber-300 p-3 rounded-lg text-xs font-medium flex items-start gap-2">
      <Clock className="w-4 h-4 shrink-0 mt-0.5" />
      <span>
        Server reported status <strong>{status || 'UNKNOWN'}</strong> via {log.channel} to {log.recipient}.
      </span>
    </div>
  );
};

export const NotificationsPage: React.FC = () => {
  const [stats, setStats] = useState<NotificationStats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);

  const [logs, setLogs] = useState<NotificationLog[]>([]);
  const [logsError, setLogsError] = useState<string | null>(null);
  const [logsLoading, setLogsLoading] = useState(true);

  const [channelFilter, setChannelFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [accountInput, setAccountInput] = useState('');
  const [appliedAccount, setAppliedAccount] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const [isSendOpen, setIsSendOpen] = useState(false);
  const [accounts, setAccounts] = useState<UserAccount[]>([]);
  const [accountsError, setAccountsError] = useState<string | null>(null);
  const [accountsLoading, setAccountsLoading] = useState(false);
  const [accountId, setAccountId] = useState('');
  const [channel, setChannel] = useState<NotificationChannel>('EMAIL');
  const [recipient, setRecipient] = useState('');
  const [template, setTemplate] = useState('');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [sendResult, setSendResult] = useState<NotificationLog | null>(null);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      setStats(await adminService.getNotificationStats());
      setStatsError(null);
    } catch (err) {
      setStatsError(errorMessage(err, 'Failed to load notification stats'));
    } finally {
      setStatsLoading(false);
    }
  }, []);

  const loadLogs = useCallback(async (forAccount: string) => {
    setLogsLoading(true);
    try {
      setLogs(await adminService.getNotificationLogs(forAccount || undefined));
      setLogsError(null);
    } catch (err) {
      setLogs([]);
      setLogsError(errorMessage(err, 'Failed to load notification logs'));
    } finally {
      setLogsLoading(false);
    }
  }, []);

  const loadAccounts = useCallback(async () => {
    setAccountsLoading(true);
    try {
      setAccounts(await adminService.getAccounts());
      setAccountsError(null);
    } catch (err) {
      setAccountsError(errorMessage(err, 'Failed to load accounts'));
    } finally {
      setAccountsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  useEffect(() => {
    void loadLogs(appliedAccount);
  }, [appliedAccount, loadLogs]);

  const refreshAll = () => {
    void loadStats();
    void loadLogs(appliedAccount);
  };

  const applyAccountFilter = (e: React.FormEvent) => {
    e.preventDefault();
    setAppliedAccount(accountInput.trim());
  };

  const clearAccountFilter = () => {
    setAccountInput('');
    setAppliedAccount('');
  };

  const filtered = useMemo(
    () =>
      logs.filter(
        (log) =>
          (channelFilter === 'ALL' || log.channel === channelFilter) &&
          (statusFilter === 'ALL' || log.status === statusFilter)
      ),
    [logs, channelFilter, statusFilter]
  );

  const selectedAccount = accounts.find((a) => a.id === accountId);

  const openSend = () => {
    setSendError(null);
    setSendResult(null);
    setIsSendOpen(true);
    if (accounts.length === 0) void loadAccounts();
  };

  const handleAccountChange = (id: string) => {
    setAccountId(id);
    setRecipient(recipientFor(accounts.find((a) => a.id === id), channel));
  };

  const handleChannelChange = (next: NotificationChannel) => {
    setChannel(next);
    if (selectedAccount) setRecipient(recipientFor(selectedAccount, next));
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!accountId || !recipient.trim() || !body.trim()) {
      setSendError('Account, recipient and body are required.');
      return;
    }
    const request: SendNotificationRequest = {
      accountId,
      recipient: recipient.trim(),
      channel,
      template: template.trim() || undefined,
      subject: subject.trim() || undefined,
      body: body.trim(),
    };
    setSending(true);
    setSendError(null);
    setSendResult(null);
    try {
      const log = await adminService.sendNotification(request);
      setSendResult(log);
      if (!appliedAccount || appliedAccount === log.accountId) {
        setLogs((prev) => [log, ...prev.filter((l) => l.id !== log.id)]);
      }
      audit({
        action: 'NOTIFICATION_SENT',
        targetType: 'ACCOUNT',
        targetId: log.accountId ?? accountId,
        details: `${log.channel} ${log.status}`,
      });
      void loadStats();
    } catch (err) {
      setSendError(errorMessage(err, 'Failed to send notification'));
    } finally {
      setSending(false);
    }
  };

  const statsUnavailable = !stats && !statsLoading;
  const delivered = stats ? (stats.countByStatus.DELIVERED ?? 0) + (stats.countByStatus.SENT ?? 0) : undefined;
  const channelBreakdown = stats
    ? Object.entries(stats.countByChannel ?? {})
        .map(([ch, count]) => `${ch} ${formatNumber(count)}`)
        .join(' · ')
    : undefined;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">Notifications</h3>
          <p className="text-xs text-slate-400">Delivery log for email, SMS and in-app notifications, with real send results.</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={refreshAll}
            disabled={statsLoading || logsLoading}
            className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${statsLoading || logsLoading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={openSend}
            className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
          >
            <Send className="w-4 h-4" />
            Send Notification
          </button>
        </div>
      </div>

      <ErrorBanner message={statsError} onRetry={() => void loadStats()} />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Notifications"
          value={formatNumber(stats?.total)}
          subtitle={channelBreakdown || undefined}
          icon={Bell}
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Last 24 Hours"
          value={formatNumber(stats?.last24Hours)}
          icon={Clock}
          iconBgColor="bg-indigo-500/10 text-indigo-400"
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Sent / Delivered"
          value={formatNumber(delivered)}
          icon={CheckCircle2}
          iconBgColor="bg-emerald-500/10 text-emerald-400"
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Failed"
          value={formatNumber(stats ? stats.countByStatus.FAILED ?? 0 : undefined)}
          subtitle={stats?.countByStatus.PENDING ? `${formatNumber(stats.countByStatus.PENDING)} pending` : undefined}
          icon={AlertTriangle}
          iconBgColor="bg-rose-500/10 text-rose-400"
          unavailable={statsUnavailable}
        />
      </div>

      <section className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-800 flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div>
            <h4 className="text-sm font-bold text-white">
              Delivery log{' '}
              {logs.length > 0 && (
                <span className="text-slate-500 font-medium">
                  ({formatNumber(filtered.length)} of {formatNumber(logs.length)})
                </span>
              )}
            </h4>
            {!appliedAccount && <p className="text-[11px] text-slate-500">Latest 500 notifications.</p>}
          </div>
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
                disabled={logsLoading}
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
            <select
              value={channelFilter}
              onChange={(e) => setChannelFilter(e.target.value)}
              className="px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            >
              <option value="ALL">All channels</option>
              {CHANNELS.map((c) => (
                <option key={c} value={c}>
                  {c.replace('_', '-')}
                </option>
              ))}
            </select>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            >
              <option value="ALL">All statuses</option>
              {LOG_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>
        {appliedAccount && (
          <div className="px-6 py-2 text-[11px] text-slate-400 border-b border-slate-800 bg-slate-950/50">
            Showing notifications for account <span className="font-mono text-white">{appliedAccount}</span>
          </div>
        )}
        {logsError && (
          <div className="p-4">
            <ErrorBanner message={logsError} onRetry={() => void loadLogs(appliedAccount)} />
          </div>
        )}
        {logsLoading ? (
          <LoadingState />
        ) : logs.length === 0 ? (
          !logsError && (
            <EmptyState title={appliedAccount ? 'No notifications for this account' : 'No notifications logged yet'} />
          )
        ) : filtered.length === 0 ? (
          <EmptyState title="No notifications match the current filters" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-4 py-3 w-8"></th>
                  <th className="px-4 py-3">Recipient</th>
                  <th className="px-4 py-3">Account</th>
                  <th className="px-4 py-3">Channel & Template</th>
                  <th className="px-4 py-3">Subject</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {filtered.map((log) => {
                  const expanded = expandedId === log.id;
                  return (
                    <React.Fragment key={log.id}>
                      <tr
                        onClick={() => setExpandedId(expanded ? null : log.id)}
                        className="hover:bg-slate-800/40 transition cursor-pointer"
                      >
                        <td className="px-4 py-3 text-slate-500">
                          {expanded ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                        </td>
                        <td className="px-4 py-3 font-semibold text-white">{log.recipient}</td>
                        <td className="px-4 py-3 font-mono text-slate-400 text-[11px]" title={log.accountId}>
                          {shortId(log.accountId)}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-1.5">
                            <ChannelIcon channel={log.channel} />
                            <span className="font-medium text-slate-300">{log.channel}</span>
                            {log.template && <span className="text-slate-500">({log.template})</span>}
                          </div>
                        </td>
                        <td className="px-4 py-3 text-slate-300">{log.subject ?? '—'}</td>
                        <td className="px-4 py-3">
                          <StatusBadge status={log.status} />
                        </td>
                        <td className="px-4 py-3 text-slate-400 whitespace-nowrap">{formatDateTime(log.createdAt)}</td>
                      </tr>
                      {expanded && (
                        <tr className="bg-slate-950/60">
                          <td></td>
                          <td colSpan={6} className="px-4 py-3 space-y-2">
                            {log.failureReason && (
                              <p className="text-rose-300">
                                <span className="font-semibold">Failure reason:</span> {log.failureReason}
                              </p>
                            )}
                            <p className="text-slate-500 text-[11px]">
                              Notification ID <span className="font-mono">{log.id}</span> · Account{' '}
                              <span className="font-mono">{log.accountId}</span>
                            </p>
                            <pre className="whitespace-pre-wrap font-sans text-slate-300 bg-slate-900 border border-slate-800 rounded-lg p-3">
                              {log.body || '(empty body)'}
                            </pre>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <Modal isOpen={isSendOpen} onClose={() => !sending && setIsSendOpen(false)} title="Send Notification">
        <form onSubmit={handleSend} className="space-y-4">
          <p className="text-[11px] text-slate-400 bg-slate-950 border border-slate-800 rounded-lg p-3">
            IN_APP notifications are stored and delivered immediately. EMAIL is only delivered if SMTP is configured on the
            server, and SMS has no provider configured — the actual result returned by the server is shown below.
          </p>
          <ErrorBanner message={sendError} />
          {sendResult && <SendResult log={sendResult} />}

          <div>
            <label className={labelClass}>Account *</label>
            {accountsError ? (
              <ErrorBanner message={accountsError} onRetry={() => void loadAccounts()} />
            ) : (
              <select
                required
                value={accountId}
                onChange={(e) => handleAccountChange(e.target.value)}
                disabled={accountsLoading}
                className={inputClass}
              >
                <option value="">{accountsLoading ? 'Loading accounts…' : 'Select an account'}</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {accountLabel(a)}
                  </option>
                ))}
              </select>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Channel</label>
              <select
                value={channel}
                onChange={(e) => handleChannelChange(e.target.value as NotificationChannel)}
                className={inputClass}
              >
                {CHANNELS.map((c) => (
                  <option key={c} value={c}>
                    {c.replace('_', '-')}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Recipient *</label>
              <input
                type="text"
                required
                value={recipient}
                onChange={(e) => setRecipient(e.target.value)}
                placeholder={channel === 'SMS' ? 'Phone number' : 'Email address'}
                className={inputClass}
              />
              {selectedAccount && !recipientFor(selectedAccount, channel) && (
                <p className="mt-1 text-[11px] text-amber-400">
                  This account has no {channel === 'SMS' ? 'phone number' : 'email'} on file.
                </p>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Template</label>
              <input
                type="text"
                value={template}
                onChange={(e) => setTemplate(e.target.value)}
                placeholder="CUSTOM"
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>Subject</label>
              <input type="text" value={subject} onChange={(e) => setSubject(e.target.value)} className={inputClass} />
            </div>
          </div>

          <div>
            <label className={labelClass}>Body *</label>
            <textarea
              required
              rows={4}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              className={inputClass}
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={() => setIsSendOpen(false)}
              disabled={sending}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
            >
              Close
            </button>
            <button
              type="submit"
              disabled={sending || accountsLoading}
              className="flex items-center gap-2 px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
            >
              <Send className="w-3.5 h-3.5" />
              {sending ? 'Sending…' : 'Send'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
