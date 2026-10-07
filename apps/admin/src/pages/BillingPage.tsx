import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AreaChart, Area, CartesianGrid, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { AlertTriangle, ChevronDown, ChevronRight, DollarSign, RefreshCw, RotateCcw, Search, Users, X } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import { PAYMENT_STATUSES } from '../types';
import type { BillingStats, PaymentTransaction } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatCard } from '../components/common/StatCard';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDateTime, formatMoney, formatNumber, shortId } from '../utils/format';

const TABLE_COLUMNS = 10;

function paymentMethodLabel(method: string | null): string {
  if (!method) return '—';
  return method.toUpperCase() === 'MPESA' ? 'M-Pesa' : method;
}

const DetailItem: React.FC<{ label: string; value: React.ReactNode; mono?: boolean }> = ({ label, value, mono }) => (
  <div className="min-w-0">
    <dt className="text-[10px] uppercase tracking-wider text-slate-500 font-semibold">{label}</dt>
    <dd className={`text-[11px] text-slate-200 break-all ${mono ? 'font-mono' : ''}`}>{value ?? '—'}</dd>
  </div>
);

// Backend sends ISO dates (yyyy-MM-dd); parse as a local day so the label doesn't shift across time zones.
function dayLabel(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  return Number.isNaN(date.getTime()) ? isoDate : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function currencyBreakdown(values: Record<string, number> | undefined): string | undefined {
  const entries = Object.entries(values ?? {});
  if (entries.length === 0) return undefined;
  return entries.map(([currency, amount]) => formatMoney(amount, currency)).join(' · ');
}

export const BillingPage: React.FC = () => {
  const [stats, setStats] = useState<BillingStats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);

  const [transactions, setTransactions] = useState<PaymentTransaction[]>([]);
  const [txError, setTxError] = useState<string | null>(null);
  const [txLoading, setTxLoading] = useState(true);

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [accountInput, setAccountInput] = useState('');
  const [appliedAccount, setAppliedAccount] = useState('');

  const [refundingId, setRefundingId] = useState<string | null>(null);
  const [expandedIds, setExpandedIds] = useState<string[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      setStats(await adminService.getBillingStats());
      setStatsError(null);
    } catch (err) {
      setStatsError(errorMessage(err, 'Failed to load billing stats'));
    } finally {
      setStatsLoading(false);
    }
  }, []);

  const loadTransactions = useCallback(async (accountId: string) => {
    setTxLoading(true);
    try {
      setTransactions(await adminService.getTransactions(accountId || undefined));
      setTxError(null);
    } catch (err) {
      setTransactions([]);
      setTxError(errorMessage(err, 'Failed to load payment transactions'));
    } finally {
      setTxLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  useEffect(() => {
    void loadTransactions(appliedAccount);
  }, [appliedAccount, loadTransactions]);

  const refreshAll = () => {
    void loadStats();
    void loadTransactions(appliedAccount);
  };

  const applyAccountFilter = (e: React.FormEvent) => {
    e.preventDefault();
    setAppliedAccount(accountInput.trim());
  };

  const clearAccountFilter = () => {
    setAccountInput('');
    setAppliedAccount('');
  };

  const filtered = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    return transactions.filter(
      (tx) =>
        (statusFilter === 'ALL' || tx.status === statusFilter) &&
        (!term ||
          [tx.id, tx.accountId, tx.externalTransactionId, tx.phoneNumber, tx.planName, tx.planId].some((value) =>
            (value ?? '').toLowerCase().includes(term)
          ))
    );
  }, [transactions, searchTerm, statusFilter]);

  const toggleExpanded = (id: string) =>
    setExpandedIds((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));

  const currency = stats?.primaryCurrency || 'KES';
  const chartData = useMemo(
    () => (stats?.dailyRevenue ?? []).map((d) => ({ date: dayLabel(d.date), amount: Number(d.amount) })),
    [stats]
  );
  const hasRevenueInWindow = chartData.some((d) => d.amount > 0);

  const handleMarkRefunded = async (tx: PaymentTransaction) => {
    const receipt = tx.externalTransactionId ? `M-Pesa receipt ${tx.externalTransactionId}` : 'no M-Pesa receipt on record';
    if (
      !window.confirm(
        `Mark ${formatMoney(tx.amount, tx.currency)} (transaction ${tx.id}, ${receipt}) as REFUNDED?\n\n` +
          'This does NOT send any money back to the customer. M-Pesa reversals must be performed in the M-Pesa (Safaricom) ' +
          'business portal first; this action only records the refund in StreamX. It cannot be undone.'
      )
    ) {
      return;
    }
    setRefundingId(tx.id);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.markTransactionRefunded(tx.id);
      setTransactions((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
      setActionSuccess(
        `Transaction ${shortId(updated.id)} is now recorded as ${updated.status} (${formatMoney(updated.amount, updated.currency)}).`
      );
      audit({
        action: 'PAYMENT_REFUNDED',
        targetType: 'PAYMENT',
        targetId: updated.id,
        reason: 'Marked refunded; M-Pesa reversal performed in the Safaricom portal',
        details: `${updated.amount} ${updated.currency}${updated.externalTransactionId ? ` · receipt ${updated.externalTransactionId}` : ''}`,
      });
      void loadStats();
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to mark the transaction as refunded'));
    } finally {
      setRefundingId(null);
    }
  };

  const statsUnavailable = !stats && !statsLoading;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">Billing & Refunds</h3>
          <p className="text-xs text-slate-400">Revenue metrics and M-Pesa payment transactions recorded by the billing service.</p>
        </div>
        <button
          onClick={refreshAll}
          disabled={statsLoading || txLoading}
          className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${statsLoading || txLoading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <ErrorBanner message={actionError} />
      <SuccessBanner message={actionSuccess} />
      <ErrorBanner message={statsError} onRetry={() => void loadStats()} />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard
          title="Total Revenue"
          value={formatMoney(stats?.totalRevenue, currency)}
          subtitle={
            stats && Object.keys(stats.revenueByCurrency ?? {}).length > 1
              ? currencyBreakdown(stats.revenueByCurrency)
              : stats
                ? `${formatNumber(stats.completedTransactions)} completed payments`
                : undefined
          }
          icon={DollarSign}
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Revenue (Last 30 Days)"
          value={formatMoney(stats?.revenueLast30Days, currency)}
          subtitle={`In ${currency}`}
          icon={DollarSign}
          iconBgColor="bg-emerald-500/10 text-emerald-400"
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Paying Accounts"
          value={formatNumber(stats?.payingAccounts)}
          icon={Users}
          iconBgColor="bg-indigo-500/10 text-indigo-400"
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Refunded"
          value={formatNumber(stats?.refundedTransactions)}
          subtitle={currencyBreakdown(stats?.refundedByCurrency)}
          icon={RotateCcw}
          iconBgColor="bg-amber-500/10 text-amber-400"
          unavailable={statsUnavailable}
        />
        <StatCard
          title="Failed Payments"
          value={formatNumber(stats?.failedTransactions)}
          subtitle={stats ? `of ${formatNumber(stats.totalTransactions)} transactions` : undefined}
          icon={AlertTriangle}
          iconBgColor="bg-rose-500/10 text-rose-400"
          unavailable={statsUnavailable}
        />
      </div>

      <section className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <div className="mb-4">
          <h4 className="text-sm font-bold text-white">Daily revenue (last 14 days)</h4>
          <p className="text-[11px] text-slate-500">Completed payments in {currency}.</p>
        </div>
        {statsLoading && !stats ? (
          <LoadingState />
        ) : !stats ? (
          <EmptyState title="Revenue chart unavailable" hint="Billing stats could not be loaded." />
        ) : chartData.length === 0 || !hasRevenueInWindow ? (
          <EmptyState title="No revenue in the last 14 days" />
        ) : (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <CartesianGrid stroke="#1e293b" strokeDasharray="3 3" />
                <XAxis dataKey="date" stroke="#64748b" fontSize={11} />
                <YAxis stroke="#64748b" fontSize={11} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', fontSize: 12 }}
                  formatter={(value) => [formatMoney(Number(value), currency), 'Revenue']}
                />
                <Area type="monotone" dataKey="amount" stroke="#dc2626" fill="#dc2626" fillOpacity={0.15} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </section>

      <section className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-800 flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <h4 className="text-sm font-bold text-white">
            Transactions{' '}
            {transactions.length > 0 && (
              <span className="text-slate-500 font-medium">
                ({formatNumber(filtered.length)} of {formatNumber(transactions.length)})
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
                disabled={txLoading}
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
            <div className="relative sm:w-64">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search ID, account, phone, receipt, plan..."
                className="w-full pl-9 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              />
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            >
              <option value="ALL">All statuses</option>
              {PAYMENT_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>
        {appliedAccount && (
          <div className="px-6 py-2 text-[11px] text-slate-400 border-b border-slate-800 bg-slate-950/50">
            Showing transactions for account <span className="font-mono text-white">{appliedAccount}</span>
          </div>
        )}
        {txError && (
          <div className="p-4">
            <ErrorBanner message={txError} onRetry={() => void loadTransactions(appliedAccount)} />
          </div>
        )}
        {txLoading ? (
          <LoadingState />
        ) : transactions.length === 0 ? (
          !txError && (
            <EmptyState
              title={appliedAccount ? 'No transactions for this account' : 'No payment transactions recorded'}
            />
          )
        ) : filtered.length === 0 ? (
          <EmptyState title="No transactions match the current filters" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="pl-4 pr-0 py-3 w-6" aria-label="Details" />
                  <th className="px-4 py-3">Transaction</th>
                  <th className="px-4 py-3">Account</th>
                  <th className="px-4 py-3">Plan</th>
                  <th className="px-4 py-3">Amount</th>
                  <th className="px-4 py-3">Phone</th>
                  <th className="px-4 py-3">M-Pesa Receipt</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Created / Updated</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {filtered.map((tx) => {
                  const expanded = expandedIds.includes(tx.id);
                  return (
                    <React.Fragment key={tx.id}>
                      <tr className="hover:bg-slate-800/40 transition align-top">
                        <td className="pl-4 pr-0 py-3">
                          <button
                            onClick={() => toggleExpanded(tx.id)}
                            title={expanded ? 'Hide details' : 'Show details'}
                            aria-expanded={expanded}
                            className="p-0.5 rounded text-slate-400 hover:text-white hover:bg-slate-800 transition"
                          >
                            {expanded ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                          </button>
                        </td>
                        <td className="px-4 py-3 font-mono font-semibold text-white" title={tx.id}>
                          {shortId(tx.id)}
                        </td>
                        <td className="px-4 py-3 font-mono text-slate-400" title={tx.accountId}>
                          {shortId(tx.accountId)}
                        </td>
                        <td className="px-4 py-3 text-slate-300" title={tx.planId ?? undefined}>
                          {tx.planName ?? (tx.planId ? <span className="font-mono text-slate-400">{shortId(tx.planId)}</span> : '—')}
                        </td>
                        <td className="px-4 py-3 font-bold text-white whitespace-nowrap">
                          {formatMoney(tx.amount, tx.currency)}
                          <span className="block text-[10px] font-medium text-slate-500">{paymentMethodLabel(tx.paymentMethod)}</span>
                        </td>
                        <td className="px-4 py-3 font-mono text-slate-400 text-[11px] whitespace-nowrap">{tx.phoneNumber ?? '—'}</td>
                        <td className="px-4 py-3 font-mono text-slate-300 text-[11px]">{tx.externalTransactionId ?? '—'}</td>
                        <td className="px-4 py-3">
                          <StatusBadge status={tx.status} />
                          {tx.errorMessage && (
                            <button
                              onClick={() => toggleExpanded(tx.id)}
                              title={tx.errorMessage}
                              className="block mt-1 max-w-[220px] truncate text-left text-[11px] text-rose-300 hover:underline"
                            >
                              {tx.errorMessage}
                            </button>
                          )}
                        </td>
                        <td className="px-4 py-3 text-slate-400 whitespace-nowrap">
                          <span className="block">{formatDateTime(tx.createdAt)}</span>
                          {tx.updatedAt && tx.updatedAt !== tx.createdAt && (
                            <span className="block text-[10px] text-slate-500">{formatDateTime(tx.updatedAt)}</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {tx.status === 'COMPLETED' && (
                            <button
                              onClick={() => void handleMarkRefunded(tx)}
                              disabled={refundingId !== null}
                              title="Record a refund that was already reversed in the M-Pesa portal"
                              className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-rose-400 text-xs font-semibold flex items-center gap-1.5 ml-auto whitespace-nowrap transition disabled:opacity-50"
                            >
                              <RotateCcw className={`w-3.5 h-3.5 ${refundingId === tx.id ? 'animate-spin' : ''}`} />
                              {refundingId === tx.id ? 'Recording…' : 'Mark refunded'}
                            </button>
                          )}
                        </td>
                      </tr>
                      {expanded && (
                        <tr className="bg-slate-950/60">
                          <td colSpan={TABLE_COLUMNS} className="px-6 py-4">
                            {tx.errorMessage && (
                              <div className="mb-3 bg-rose-500/10 border border-rose-500/20 text-rose-200 rounded-lg p-3 text-[11px]">
                                <span className="font-semibold text-rose-300">Failure reason: </span>
                                {tx.errorMessage}
                              </div>
                            )}
                            <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                              <DetailItem label="Transaction ID" value={tx.id} mono />
                              <DetailItem label="Account ID" value={tx.accountId} mono />
                              <DetailItem label="Plan" value={tx.planName} />
                              <DetailItem label="Plan ID" value={tx.planId} mono />
                              <DetailItem label="Subscription ID" value={tx.subscriptionId} mono />
                              <DetailItem label="Payment method" value={paymentMethodLabel(tx.paymentMethod)} />
                              <DetailItem label="Phone number" value={tx.phoneNumber} mono />
                              <DetailItem label="M-Pesa receipt" value={tx.externalTransactionId} mono />
                              <DetailItem label="Created" value={formatDateTime(tx.createdAt)} />
                              <DetailItem label="Last updated" value={formatDateTime(tx.updatedAt)} />
                            </dl>
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
    </div>
  );
};
