import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Search, Lock, Unlock, Phone, Mail, Users, UserCheck, UserX, UserPlus, RefreshCw, BadgeCheck } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { AccountStats, UserAccount } from '../types';
import { useAuth } from '../context/AuthContext';
import { StatCard } from '../components/common/StatCard';
import { StatusBadge } from '../components/common/StatusBadge';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDate, formatNumber, humanizeRole } from '../utils/format';

export const UsersPage: React.FC = () => {
  const { accountId: currentAccountId } = useAuth();

  const [accounts, setAccounts] = useState<UserAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [stats, setStats] = useState<AccountStats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const [pendingId, setPendingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const loadAccounts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAccounts(await adminService.getAccounts());
    } catch (err) {
      setError(errorMessage(err, 'Failed to load accounts'));
    } finally {
      setLoading(false);
    }
  }, []);

  const loadStats = useCallback(async () => {
    setStatsError(null);
    try {
      setStats(await adminService.getAccountStats());
    } catch (err) {
      setStats(null);
      setStatsError(errorMessage(err, 'Failed to load account statistics'));
    }
  }, []);

  const loadAll = useCallback(() => {
    void loadAccounts();
    void loadStats();
  }, [loadAccounts, loadStats]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const statusOptions = useMemo(
    () => Array.from(new Set(accounts.map((a) => a.status))).sort(),
    [accounts]
  );

  const filteredAccounts = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return accounts.filter((acc) => {
      const matchesStatus = statusFilter === 'ALL' || acc.status === statusFilter;
      if (!matchesStatus) return false;
      if (!term) return true;
      return (
        (acc.email ?? '').toLowerCase().includes(term) ||
        acc.id.toLowerCase().includes(term) ||
        (acc.phoneNumber ?? '').toLowerCase().includes(term)
      );
    });
  }, [accounts, searchTerm, statusFilter]);

  const handleToggleBlock = async (account: UserAccount) => {
    const nextBlocked = !account.blocked;
    const label = account.email ?? account.phoneNumber ?? account.id;
    const confirmed = window.confirm(
      nextBlocked
        ? `Block ${label}? Their account will be suspended and all active sessions revoked.`
        : `Unblock ${label}? Their account will be reactivated.`
    );
    if (!confirmed) return;

    setPendingId(account.id);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.setAccountBlocked(account.id, nextBlocked);
      setAccounts((prev) => prev.map((acc) => (acc.id === updated.id ? updated : acc)));
      audit({
        action: nextBlocked ? 'ACCOUNT_BLOCKED' : 'ACCOUNT_UNBLOCKED',
        targetType: 'ACCOUNT',
        targetId: account.id,
      });
      setActionSuccess(`${label} is now ${updated.blocked ? 'blocked' : 'active'} (status: ${updated.status}).`);
      void loadStats();
    } catch (err) {
      setActionError(errorMessage(err, `Failed to ${nextBlocked ? 'block' : 'unblock'} account`));
    } finally {
      setPendingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">User Accounts & Access Control</h3>
          <p className="text-xs text-slate-400">Registered accounts, roles and verification state, with account block/unblock.</p>
        </div>

        <div className="flex flex-col sm:flex-row gap-3 w-full sm:w-auto">
          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search email, phone or account ID..."
              className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-300 focus:outline-none focus:border-red-500"
          >
            <option value="ALL">All statuses</option>
            {statusOptions.map((s) => (
              <option key={s} value={s}>
                {s.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
          <button
            onClick={loadAll}
            disabled={loading}
            className="px-3 py-2 bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-lg text-xs font-semibold text-slate-300 flex items-center gap-1.5 disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {statsError ? (
        <ErrorBanner message={statsError} onRetry={() => void loadStats()} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard title="Total accounts" value={formatNumber(stats?.totalAccounts)} icon={Users} />
          <StatCard
            title="Active"
            value={formatNumber(stats?.activeAccounts)}
            icon={UserCheck}
            iconBgColor="bg-emerald-500/10 text-emerald-400"
          />
          <StatCard
            title="Suspended / banned"
            value={formatNumber(stats?.suspendedAccounts)}
            icon={UserX}
            iconBgColor="bg-rose-500/10 text-rose-400"
          />
          <StatCard
            title="New (last 7 days)"
            value={formatNumber(stats?.newAccountsLast7Days)}
            icon={UserPlus}
            iconBgColor="bg-blue-500/10 text-blue-400"
          />
        </div>
      )}

      <ErrorBanner message={actionError} />
      <SuccessBanner message={actionSuccess} />

      {error ? (
        <ErrorBanner message={error} onRetry={() => void loadAccounts()} />
      ) : loading && accounts.length === 0 ? (
        <LoadingState label="Loading accounts…" />
      ) : accounts.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl">
          <EmptyState title="No accounts registered yet" hint="Accounts appear here as soon as users sign up." />
        </div>
      ) : (
        <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
              <tr>
                <th className="px-6 py-4">Account ID & Email</th>
                <th className="px-6 py-4">Phone Number</th>
                <th className="px-6 py-4">Roles</th>
                <th className="px-6 py-4">Registered</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {filteredAccounts.map((account) => {
                const isSelf = account.id === currentAccountId;
                const isPending = pendingId === account.id;
                return (
                  <tr
                    key={account.id}
                    className={`transition ${isSelf ? 'bg-red-500/5 hover:bg-red-500/10' : 'hover:bg-slate-800/40'}`}
                  >
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <Mail className="w-4 h-4 text-slate-500 shrink-0" />
                        <div>
                          <span className="font-semibold text-white flex items-center gap-1.5">
                            {account.email ?? <span className="text-slate-500 font-normal">No email</span>}
                            {account.email && account.emailVerified && (
                              <BadgeCheck className="w-3.5 h-3.5 text-emerald-400" aria-label="Email verified" />
                            )}
                            {isSelf && (
                              <span className="px-1.5 py-0.5 rounded bg-red-600/20 border border-red-500/30 text-[10px] text-red-300 font-semibold">
                                You
                              </span>
                            )}
                          </span>
                          <span className="text-[10px] text-slate-500 font-mono">{account.id}</span>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {account.phoneNumber ? (
                        <div className="flex items-center gap-1.5 text-slate-300">
                          <Phone className="w-3.5 h-3.5 text-slate-500" />
                          {account.phoneNumber}
                          {!account.phoneVerified && <span className="text-[10px] text-amber-400">(unverified)</span>}
                        </div>
                      ) : (
                        <span className="text-slate-600">—</span>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex flex-wrap gap-1">
                        {account.roles.length === 0 && <span className="text-slate-600">—</span>}
                        {account.roles.map((role) => (
                          <span
                            key={role}
                            className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-[10px] font-medium text-slate-300"
                          >
                            {humanizeRole(role)}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-slate-400">{formatDate(account.createdAt)}</td>
                    <td className="px-6 py-4">
                      <StatusBadge status={account.status} />
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => void handleToggleBlock(account)}
                        disabled={isPending || (isSelf && !account.blocked)}
                        title={isSelf && !account.blocked ? 'You cannot block your own account' : undefined}
                        className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 ml-auto transition disabled:opacity-40 disabled:cursor-not-allowed ${
                          account.blocked
                            ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                            : 'bg-rose-600 hover:bg-rose-700 text-white'
                        }`}
                      >
                        {isPending ? (
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                        ) : account.blocked ? (
                          <Unlock className="w-3.5 h-3.5" />
                        ) : (
                          <Lock className="w-3.5 h-3.5" />
                        )}
                        {account.blocked ? 'Unblock' : 'Block'}
                      </button>
                    </td>
                  </tr>
                );
              })}
              {filteredAccounts.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                    No accounts match the current search or filter.
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
