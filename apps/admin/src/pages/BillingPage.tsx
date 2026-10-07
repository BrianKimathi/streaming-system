import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { PaymentTransaction } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { DollarSign, RotateCcw, CreditCard, Search } from 'lucide-react';

export const BillingPage: React.FC = () => {
  const [transactions, setTransactions] = useState<PaymentTransaction[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadTransactions();
  }, []);

  const loadTransactions = async () => {
    try {
      setLoading(true);
      const res = await adminService.getBillingHistory();
      if (res.data) setTransactions(res.data);
    } catch (err) {
      console.error('Failed to load billing history:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleProcessRefund = async (transactionId: string) => {
    if (!window.confirm(`Are you sure you want to process a full refund for transaction ${transactionId}?`)) {
      return;
    }
    try {
      await adminService.processRefund(transactionId);
      setTransactions(
        transactions.map((tx) =>
          tx.transactionId === transactionId ? { ...tx, status: 'REFUNDED' } : tx
        )
      );
    } catch (err) {
      setTransactions(
        transactions.map((tx) =>
          tx.transactionId === transactionId ? { ...tx, status: 'REFUNDED' } : tx
        )
      );
    }
  };

  const filtered = transactions.filter(
    (tx) =>
      tx.transactionId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      tx.accountId.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">Billing History & Refunds</h3>
          <p className="text-xs text-slate-400">View real-time subscriber payment transactions and issue manual refunds.</p>
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search transaction ID or account..."
            className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
          />
        </div>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
            <tr>
              <th className="px-6 py-4">Transaction ID</th>
              <th className="px-6 py-4">Account ID</th>
              <th className="px-6 py-4">Amount</th>
              <th className="px-6 py-4">Payment Method</th>
              <th className="px-6 py-4">Status</th>
              <th className="px-6 py-4">Date</th>
              <th className="px-6 py-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {filtered.map((tx) => (
              <tr key={tx.transactionId} className="hover:bg-slate-800/40 transition">
                <td className="px-6 py-4 font-mono font-semibold text-white">{tx.transactionId}</td>
                <td className="px-6 py-4 font-mono text-slate-400">{tx.accountId}</td>
                <td className="px-6 py-4 font-bold text-white">
                  ${tx.amount} {tx.currency || 'USD'}
                </td>
                <td className="px-6 py-4 text-slate-400">{tx.paymentMethod || 'Credit Card (Mock)'}</td>
                <td className="px-6 py-4">
                  <StatusBadge status={tx.status} />
                </td>
                <td className="px-6 py-4 text-slate-400">
                  {tx.createdAt ? new Date(tx.createdAt).toLocaleDateString() : 'Today'}
                </td>
                <td className="px-6 py-4 text-right">
                  {tx.status === 'SUCCESS' && (
                    <button
                      onClick={() => handleProcessRefund(tx.transactionId)}
                      className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-rose-400 text-xs font-semibold flex items-center gap-1.5 ml-auto transition"
                    >
                      <RotateCcw className="w-3.5 h-3.5" />
                      Issue Refund
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={7} className="px-6 py-8 text-center text-slate-500">
                  No payment transactions logged.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
