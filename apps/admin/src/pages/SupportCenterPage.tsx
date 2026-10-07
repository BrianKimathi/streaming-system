import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import {
  LifeBuoy,
  User,
  CreditCard,
  Smartphone,
  Plus,
  RefreshCw,
  UserCheck,
  Receipt,
  Loader2,
} from 'lucide-react';
import { adminService } from '../services/adminService';
import { errorMessage } from '../api/client';
import type {
  DeviceRegistration,
  PaymentTransaction,
  Subscription,
  SupportTicket,
  TicketPriority,
  TicketStatus,
} from '../types';
import { useAuth } from '../context/AuthContext';
import { StatusBadge } from '../components/common/StatusBadge';
import { Modal } from '../components/common/Modal';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDateTime, formatMoney, shortId } from '../utils/format';

const TICKET_STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const TICKET_PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
const TICKET_CATEGORIES = ['ACCOUNT', 'BILLING', 'SUBSCRIPTION', 'PLAYBACK', 'DEVICE'] as const;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const PRIORITY_COLORS: Record<TicketPriority, string> = {
  LOW: 'bg-slate-800 text-slate-300 border-slate-700',
  MEDIUM: 'bg-blue-500/10 text-blue-300 border-blue-500/20',
  HIGH: 'bg-amber-500/10 text-amber-300 border-amber-500/20',
  URGENT: 'bg-rose-500/10 text-rose-300 border-rose-500/20',
};

const inputClass =
  'w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500';
const selectClass =
  'px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-300 focus:outline-none focus:border-red-500';

const PriorityBadge: React.FC<{ priority: TicketPriority }> = ({ priority }) => (
  <span className={`px-2 py-0.5 rounded border text-[10px] font-semibold ${PRIORITY_COLORS[priority] ?? PRIORITY_COLORS.LOW}`}>
    {priority}
  </span>
);

interface SectionState<T> {
  loading: boolean;
  error: string | null;
  data: T | null;
}

const initialSection = <T,>(): SectionState<T> => ({ loading: true, error: null, data: null });

const CustomerContext: React.FC<{ accountId: string }> = ({ accountId }) => {
  const [subscription, setSubscription] = useState<SectionState<Subscription>>(initialSection);
  const [devices, setDevices] = useState<SectionState<DeviceRegistration[]>>(initialSection);
  const [transactions, setTransactions] = useState<SectionState<PaymentTransaction[]>>(initialSection);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setSubscription(initialSection());
    setDevices(initialSection());
    setTransactions(initialSection());

    adminService
      .getAccountSubscription(accountId)
      .then((data) => !cancelled && setSubscription({ loading: false, error: null, data }))
      .catch((err: unknown) => {
        if (cancelled) return;
        if (axios.isAxiosError(err) && err.response?.status === 404) {
          setSubscription({ loading: false, error: null, data: null });
        } else {
          setSubscription({ loading: false, error: errorMessage(err, 'Failed to load subscription'), data: null });
        }
      });

    adminService
      .getDevices(accountId)
      .then((data) => !cancelled && setDevices({ loading: false, error: null, data }))
      .catch(
        (err: unknown) =>
          !cancelled && setDevices({ loading: false, error: errorMessage(err, 'Failed to load devices'), data: null })
      );

    adminService
      .getTransactions(accountId)
      .then((data) => !cancelled && setTransactions({ loading: false, error: null, data }))
      .catch(
        (err: unknown) =>
          !cancelled &&
          setTransactions({ loading: false, error: errorMessage(err, 'Failed to load transactions'), data: null })
      );

    return () => {
      cancelled = true;
    };
  }, [accountId, reloadKey]);

  const retry = () => setReloadKey((k) => k + 1);
  const activeDevices = devices.data?.filter((d) => d.status === 'ACTIVE').length ?? 0;

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
      <div className="flex items-center justify-between border-b border-slate-800 pb-3">
        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
          <User className="w-4 h-4 text-blue-400" />
          Customer context
        </h4>
        <span className="text-xs font-mono text-slate-400">Account {accountId}</span>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
        <div className="bg-slate-950 border border-slate-800 p-3 rounded-lg space-y-2">
          <span className="text-slate-400 text-[10px] uppercase font-semibold flex items-center gap-1.5">
            <CreditCard className="w-3.5 h-3.5" /> Subscription
          </span>
          {subscription.loading ? (
            <Loader2 className="w-4 h-4 animate-spin text-red-500" />
          ) : subscription.error ? (
            <ErrorBanner message={subscription.error} onRetry={retry} />
          ) : subscription.data ? (
            <div className="space-y-1">
              <div className="font-bold text-white text-sm">
                {subscription.data.plan.name}{' '}
                <span className="text-[10px] text-slate-500 font-normal">v{subscription.data.plan.version}</span>
              </div>
              <StatusBadge status={subscription.data.status} />
              <div className="text-[11px] text-slate-400">
                {formatMoney(subscription.data.plan.price, subscription.data.plan.currency)} /{' '}
                {subscription.data.plan.billingInterval.toLowerCase()}
              </div>
              <div className="text-[11px] text-slate-500">
                Period ends {formatDateTime(subscription.data.currentPeriodEnd)}
                {subscription.data.cancelAtPeriodEnd && ' (cancels at period end)'}
              </div>
            </div>
          ) : (
            <span className="text-slate-500">No subscription</span>
          )}
        </div>

        <div className="bg-slate-950 border border-slate-800 p-3 rounded-lg space-y-2">
          <span className="text-slate-400 text-[10px] uppercase font-semibold flex items-center gap-1.5">
            <Smartphone className="w-3.5 h-3.5" /> Devices
          </span>
          {devices.loading ? (
            <Loader2 className="w-4 h-4 animate-spin text-red-500" />
          ) : devices.error ? (
            <ErrorBanner message={devices.error} onRetry={retry} />
          ) : devices.data && devices.data.length > 0 ? (
            <div className="space-y-1.5">
              <div className="font-bold text-white text-sm">
                {activeDevices} active / {devices.data.length} registered
              </div>
              <ul className="space-y-1">
                {devices.data.slice(0, 5).map((d) => (
                  <li key={d.id} className="flex items-center justify-between gap-2 text-[11px]">
                    <span className="text-slate-300 truncate">
                      {d.deviceName ?? d.deviceType} {d.platform ? `· ${d.platform}` : ''}
                    </span>
                    <StatusBadge status={d.status} />
                  </li>
                ))}
              </ul>
              {devices.data.length > 5 && (
                <span className="text-[10px] text-slate-500">+{devices.data.length - 5} more</span>
              )}
            </div>
          ) : (
            <span className="text-slate-500">No registered devices</span>
          )}
        </div>

        <div className="bg-slate-950 border border-slate-800 p-3 rounded-lg space-y-2">
          <span className="text-slate-400 text-[10px] uppercase font-semibold flex items-center gap-1.5">
            <Receipt className="w-3.5 h-3.5" /> Recent payments
          </span>
          {transactions.loading ? (
            <Loader2 className="w-4 h-4 animate-spin text-red-500" />
          ) : transactions.error ? (
            <ErrorBanner message={transactions.error} onRetry={retry} />
          ) : transactions.data && transactions.data.length > 0 ? (
            <ul className="space-y-1.5">
              {transactions.data.slice(0, 5).map((t) => (
                <li key={t.id} className="flex items-center justify-between gap-2 text-[11px]">
                  <span className="text-slate-300">
                    {formatMoney(t.amount, t.currency)}
                    <span className="block text-[10px] text-slate-500">{formatDateTime(t.createdAt)}</span>
                  </span>
                  <StatusBadge status={t.status} />
                </li>
              ))}
            </ul>
          ) : (
            <span className="text-slate-500">No transactions</span>
          )}
        </div>
      </div>
    </div>
  );
};

interface NewTicketForm {
  accountId: string;
  userEmail: string;
  category: string;
  priority: TicketPriority;
  subject: string;
  body: string;
}

const emptyForm: NewTicketForm = {
  accountId: '',
  userEmail: '',
  category: 'ACCOUNT',
  priority: 'MEDIUM',
  subject: '',
  body: '',
};

export const SupportCenterPage: React.FC = () => {
  const { email: currentEmail } = useAuth();

  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<'ALL' | TicketStatus>('ALL');
  const [priorityFilter, setPriorityFilter] = useState<'ALL' | TicketPriority>('ALL');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [assigneeDraft, setAssigneeDraft] = useState<{ ticketId: string; value: string } | null>(null);
  const [pending, setPending] = useState<'status' | 'assign' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<NewTicketForm>(emptyForm);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await adminService.getSupportTickets();
      setTickets(data);
      setSelectedId((current) => (data.find((t) => t.id === current) ?? data[0] ?? null)?.id ?? null);
      setAssigneeDraft(null);
    } catch (err) {
      setError(errorMessage(err, 'Failed to load support tickets'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const filteredTickets = useMemo(
    () =>
      tickets.filter(
        (t) =>
          (statusFilter === 'ALL' || t.status === statusFilter) &&
          (priorityFilter === 'ALL' || t.priority === priorityFilter)
      ),
    [tickets, statusFilter, priorityFilter]
  );

  const selectedTicket = tickets.find((t) => t.id === selectedId) ?? null;

  const savedAssignee = selectedTicket?.assignedAgentEmail ?? '';
  const assigneeInput =
    assigneeDraft && assigneeDraft.ticketId === selectedTicket?.id ? assigneeDraft.value : savedAssignee;

  const selectTicket = (ticket: SupportTicket) => {
    setSelectedId(ticket.id);
    setAssigneeDraft(null);
    setActionError(null);
    setActionSuccess(null);
  };

  const applyUpdate = async (
    ticket: SupportTicket,
    changes: { status?: string; assignedAgentEmail?: string },
    kind: 'status' | 'assign'
  ) => {
    setPending(kind);
    setActionError(null);
    setActionSuccess(null);
    try {
      const updated = await adminService.updateSupportTicket(ticket.id, changes);
      setTickets((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
      setAssigneeDraft(null);
      setActionSuccess(
        kind === 'status'
          ? `Ticket status changed to ${updated.status.replace(/_/g, ' ')}.`
          : updated.assignedAgentEmail
            ? `Ticket assigned to ${updated.assignedAgentEmail}.`
            : 'Ticket unassigned.'
      );
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to update ticket'));
    } finally {
      setPending(null);
    }
  };

  const openCreate = () => {
    setForm(emptyForm);
    setCreateError(null);
    setShowCreate(true);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const accountId = form.accountId.trim();
    if (accountId && !UUID_PATTERN.test(accountId)) {
      setCreateError('Account ID must be a valid UUID.');
      return;
    }
    if (!form.subject.trim()) {
      setCreateError('Subject is required.');
      return;
    }
    setCreating(true);
    setCreateError(null);
    try {
      const created = await adminService.createSupportTicket({
        accountId: accountId || undefined,
        userEmail: form.userEmail.trim() || undefined,
        category: form.category,
        priority: form.priority,
        subject: form.subject.trim(),
        body: form.body.trim() || undefined,
      });
      setTickets((prev) => [created, ...prev.filter((t) => t.id !== created.id)]);
      selectTicket(created);
      setShowCreate(false);
      setActionSuccess(`Ticket "${created.subject}" created.`);
    } catch (err) {
      setCreateError(errorMessage(err, 'Failed to create ticket'));
    } finally {
      setCreating(false);
    }
  };

  const assigneeChanged = assigneeInput.trim() !== (selectedTicket?.assignedAgentEmail ?? '');

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <LifeBuoy className="w-5 h-5 text-red-500" />
            Support Ticket Center
          </h3>
          <p className="text-xs text-slate-400">Triage customer tickets with live account, subscription, device and payment context.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as 'ALL' | TicketStatus)}
            className={selectClass}
          >
            <option value="ALL">All statuses</option>
            {TICKET_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
          <select
            value={priorityFilter}
            onChange={(e) => setPriorityFilter(e.target.value as 'ALL' | TicketPriority)}
            className={selectClass}
          >
            <option value="ALL">All priorities</option>
            {TICKET_PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {p}
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
            onClick={openCreate}
            className="px-3 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition"
          >
            <Plus className="w-3.5 h-3.5" />
            New ticket
          </button>
        </div>
      </div>

      <SuccessBanner message={actionSuccess} />

      {error ? (
        <ErrorBanner message={error} onRetry={() => void load()} />
      ) : loading && tickets.length === 0 ? (
        <LoadingState label="Loading support tickets…" />
      ) : tickets.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl">
          <EmptyState title="No support tickets yet" hint="Create a ticket to start tracking a customer issue." />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden divide-y divide-slate-800 self-start">
            <div className="p-4 bg-slate-950 text-xs font-bold text-white uppercase tracking-wider">
              Tickets ({filteredTickets.length} of {tickets.length})
            </div>
            {filteredTickets.length === 0 && (
              <p className="p-6 text-center text-xs text-slate-500">No tickets match the selected filters.</p>
            )}
            <div className="max-h-[70vh] overflow-y-auto divide-y divide-slate-800">
              {filteredTickets.map((ticket) => (
                <button
                  key={ticket.id}
                  onClick={() => selectTicket(ticket)}
                  className={`w-full text-left p-4 transition ${
                    selectedId === ticket.id ? 'bg-slate-800 border-l-4 border-l-red-600' : 'hover:bg-slate-800/50'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1 gap-2">
                    <span className="font-mono text-[11px] text-slate-400">{shortId(ticket.id)}</span>
                    <div className="flex items-center gap-1.5">
                      <PriorityBadge priority={ticket.priority} />
                      <StatusBadge status={ticket.status} />
                    </div>
                  </div>
                  <p className="text-xs font-semibold text-slate-200 truncate">{ticket.subject}</p>
                  <p className="text-[11px] text-slate-400 mt-1 truncate">
                    {ticket.userEmail ?? 'No email'} · {formatDateTime(ticket.createdAt)}
                  </p>
                </button>
              ))}
            </div>
          </div>

          {selectedTicket ? (
            <div className="lg:col-span-2 space-y-6">
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3 border-b border-slate-800 pb-3">
                  <div>
                    <h4 className="text-base font-bold text-white">{selectedTicket.subject}</h4>
                    <div className="flex items-center gap-2 mt-1 text-xs text-slate-400">
                      <span>{selectedTicket.category}</span>
                      <PriorityBadge priority={selectedTicket.priority} />
                      <StatusBadge status={selectedTicket.status} />
                    </div>
                  </div>
                  <span className="font-mono text-[10px] text-slate-500">{selectedTicket.id}</span>
                </div>

                <ErrorBanner message={actionError} />

                <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                  <div>
                    <dt className="text-[10px] uppercase text-slate-500 font-semibold">Customer email</dt>
                    <dd className="text-slate-200">{selectedTicket.userEmail ?? '—'}</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] uppercase text-slate-500 font-semibold">Account ID</dt>
                    <dd className="text-slate-200 font-mono break-all">{selectedTicket.accountId ?? '—'}</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] uppercase text-slate-500 font-semibold">Assigned agent</dt>
                    <dd className="text-slate-200">{selectedTicket.assignedAgentEmail ?? 'Unassigned'}</dd>
                  </div>
                  <div>
                    <dt className="text-[10px] uppercase text-slate-500 font-semibold">Created / updated</dt>
                    <dd className="text-slate-200">
                      {formatDateTime(selectedTicket.createdAt)}
                      <span className="block text-slate-500">{formatDateTime(selectedTicket.updatedAt)}</span>
                    </dd>
                  </div>
                </dl>

                <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl">
                  {selectedTicket.body ? (
                    <p className="text-xs text-slate-300 leading-relaxed whitespace-pre-wrap">{selectedTicket.body}</p>
                  ) : (
                    <p className="text-xs text-slate-500 italic">No description provided.</p>
                  )}
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Status</label>
                    <div className="flex items-center gap-2">
                      <select
                        value={selectedTicket.status}
                        disabled={pending !== null}
                        onChange={(e) =>
                          void applyUpdate(selectedTicket, { status: e.target.value }, 'status')
                        }
                        className={`${selectClass} w-full disabled:opacity-50`}
                      >
                        {TICKET_STATUSES.map((s) => (
                          <option key={s} value={s}>
                            {s.replace(/_/g, ' ')}
                          </option>
                        ))}
                      </select>
                      {pending === 'status' && <Loader2 className="w-4 h-4 animate-spin text-red-500" />}
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">Assign agent</label>
                    <div className="flex items-center gap-2">
                      <input
                        type="email"
                        value={assigneeInput}
                        onChange={(e) => setAssigneeDraft({ ticketId: selectedTicket.id, value: e.target.value })}
                        placeholder="agent@example.com (empty = unassign)"
                        disabled={pending !== null}
                        className={inputClass}
                      />
                      <button
                        onClick={() =>
                          void applyUpdate(selectedTicket, { assignedAgentEmail: assigneeInput.trim() }, 'assign')
                        }
                        disabled={pending !== null || !assigneeChanged}
                        className="px-3 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5"
                      >
                        {pending === 'assign' && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                        Save
                      </button>
                    </div>
                    {currentEmail && selectedTicket.assignedAgentEmail !== currentEmail && (
                      <button
                        onClick={() =>
                          void applyUpdate(selectedTicket, { assignedAgentEmail: currentEmail }, 'assign')
                        }
                        disabled={pending !== null}
                        className="mt-1.5 text-[11px] text-red-400 hover:text-red-300 flex items-center gap-1 disabled:opacity-50"
                      >
                        <UserCheck className="w-3.5 h-3.5" />
                        Assign to me
                      </button>
                    )}
                  </div>
                </div>
              </div>

              {selectedTicket.accountId ? (
                <CustomerContext key={selectedTicket.accountId} accountId={selectedTicket.accountId} />
              ) : (
                <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 text-xs text-slate-500">
                  This ticket is not linked to an account, so no customer context is available.
                </div>
              )}
            </div>
          ) : (
            <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-xl p-8 text-center text-xs text-slate-500">
              Select a ticket to view its details.
            </div>
          )}
        </div>
      )}

      <Modal isOpen={showCreate} onClose={() => !creating && setShowCreate(false)} title="New support ticket">
        <form onSubmit={(e) => void handleCreate(e)} className="space-y-4">
          <ErrorBanner message={createError} />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Account ID (optional)</label>
              <input
                value={form.accountId}
                onChange={(e) => setForm({ ...form, accountId: e.target.value })}
                placeholder="UUID"
                className={`${inputClass} font-mono`}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Customer email (optional)</label>
              <input
                type="email"
                value={form.userEmail}
                onChange={(e) => setForm({ ...form, userEmail: e.target.value })}
                placeholder="customer@example.com"
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Category</label>
              <select
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
                className={`${selectClass} w-full`}
              >
                {TICKET_CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Priority</label>
              <select
                value={form.priority}
                onChange={(e) => setForm({ ...form, priority: e.target.value as TicketPriority })}
                className={`${selectClass} w-full`}
              >
                {TICKET_PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Subject</label>
            <input
              required
              value={form.subject}
              onChange={(e) => setForm({ ...form, subject: e.target.value })}
              className={inputClass}
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Description</label>
            <textarea
              rows={4}
              value={form.body}
              onChange={(e) => setForm({ ...form, body: e.target.value })}
              className={inputClass}
            />
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
              Create ticket
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
