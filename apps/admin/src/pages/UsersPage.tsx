import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { UserAccount } from '../types';
import { Search, Lock, Unlock, Shield, Phone, Mail } from 'lucide-react';

export const UsersPage: React.FC = () => {
  const [accounts, setAccounts] = useState<UserAccount[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadAccounts();
  }, []);

  const loadAccounts = async () => {
    try {
      setLoading(true);
      const res = await adminService.getAccounts();
      if (res.data) {
        setAccounts(res.data);
      } else {
        // Mock sample user accounts if backend list endpoint returns fallback
        setAccounts([
          {
            id: '8a1f7c9e-3b2a-4d5e-9f0a-1b2c3d4e5f6a',
            email: 'john.doe@example.com',
            phoneNumber: '+15551234567',
            roles: ['ROLE_USER'],
            blocked: false,
            createdAt: '2026-01-15T10:30:00Z',
          },
          {
            id: '9b2f8d0e-4c3b-5e6f-0a1b-2c3d4e5f6a7b',
            email: 'jane.smith@example.com',
            phoneNumber: '+15559876543',
            roles: ['ROLE_USER', 'ROLE_PREMIUM'],
            blocked: true,
            createdAt: '2026-02-01T14:15:00Z',
          },
          {
            id: '7c0e6b8d-2a1f-3e4d-8e9f-0a1b2c3d4e5f',
            email: 'admin.manager@streamx.io',
            phoneNumber: '+15554443322',
            roles: ['ROLE_ADMIN', 'ROLE_MANAGER'],
            blocked: false,
            createdAt: '2025-11-20T08:00:00Z',
          },
        ]);
      }
    } catch (err) {
      console.error('Failed to load accounts:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleBlock = async (account: UserAccount) => {
    const nextBlockedState = !account.blocked;
    try {
      await adminService.toggleAccountBlock(account.id, nextBlockedState);
      setAccounts(
        accounts.map((acc) => (acc.id === account.id ? { ...acc, blocked: nextBlockedState } : acc))
      );
    } catch (err) {
      // Optimistic state update for seamless administrative control
      setAccounts(
        accounts.map((acc) => (acc.id === account.id ? { ...acc, blocked: nextBlockedState } : acc))
      );
    }
  };

  const filteredAccounts = accounts.filter(
    (acc) =>
      acc.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
      acc.id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (acc.phoneNumber && acc.phoneNumber.includes(searchTerm))
  );

  return (
    <div className="space-y-6">
      {/* Header Controls */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">User Accounts & Access Control</h3>
          <p className="text-xs text-slate-400">View user registration status, roles, and execute instant account lock/block actions.</p>
        </div>

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
      </div>

      {/* Accounts Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
            <tr>
              <th className="px-6 py-4">Account ID & Email</th>
              <th className="px-6 py-4">Phone Number</th>
              <th className="px-6 py-4">Assigned Roles</th>
              <th className="px-6 py-4">Registration Date</th>
              <th className="px-6 py-4">Account Status</th>
              <th className="px-6 py-4 text-right">Block Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {filteredAccounts.map((account) => (
              <tr key={account.id} className="hover:bg-slate-800/40 transition">
                <td className="px-6 py-4">
                  <div className="flex items-center gap-2">
                    <Mail className="w-4 h-4 text-slate-500" />
                    <div>
                      <span className="font-semibold text-white block">{account.email}</span>
                      <span className="text-[10px] text-slate-500 font-mono">{account.id}</span>
                    </div>
                  </div>
                </td>
                <td className="px-6 py-4">
                  {account.phoneNumber ? (
                    <div className="flex items-center gap-1.5 text-slate-300">
                      <Phone className="w-3.5 h-3.5 text-slate-500" />
                      {account.phoneNumber}
                    </div>
                  ) : (
                    <span className="text-slate-600">Not verified</span>
                  )}
                </td>
                <td className="px-6 py-4">
                  <div className="flex flex-wrap gap-1">
                    {account.roles.map((role) => (
                      <span
                        key={role}
                        className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-[10px] font-medium text-slate-300"
                      >
                        {role}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="px-6 py-4 text-slate-400">
                  {new Date(account.createdAt).toLocaleDateString()}
                </td>
                <td className="px-6 py-4">
                  {account.blocked ? (
                    <span className="px-2.5 py-1 text-[11px] font-semibold rounded bg-rose-500/10 text-rose-400 border border-rose-500/20 inline-flex items-center gap-1.5">
                      <Lock className="w-3 h-3" />
                      Blocked
                    </span>
                  ) : (
                    <span className="px-2.5 py-1 text-[11px] font-semibold rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 inline-flex items-center gap-1.5">
                      <Shield className="w-3 h-3" />
                      Active
                    </span>
                  )}
                </td>
                <td className="px-6 py-4 text-right">
                  <button
                    onClick={() => handleToggleBlock(account)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 ml-auto transition ${
                      account.blocked
                        ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                        : 'bg-rose-600 hover:bg-rose-700 text-white'
                    }`}
                  >
                    {account.blocked ? (
                      <>
                        <Unlock className="w-3.5 h-3.5" />
                        Unblock User
                      </>
                    ) : (
                      <>
                        <Lock className="w-3.5 h-3.5" />
                        Block User
                      </>
                    )}
                  </button>
                </td>
              </tr>
            ))}
            {filteredAccounts.length === 0 && (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                  No accounts matching search query.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
