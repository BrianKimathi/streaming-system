import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { SupportTicket } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { LifeBuoy, User, CreditCard, Smartphone, CheckCircle, ShieldAlert, MessageSquare } from 'lucide-react';

export const SupportCenterPage: React.FC = () => {
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<SupportTicket | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadTickets();
  }, []);

  const loadTickets = async () => {
    try {
      setLoading(true);
      const res = await adminService.getSupportTickets();
      if (res.data && res.data.length > 0) {
        setTickets(res.data);
        setSelectedTicket(res.data[0]);
      } else {
        const mockTickets: SupportTicket[] = [
          {
            id: 'TCK-8801',
            accountId: '8a1f7c9e-3b2a-4d5e-9f0a-1b2c3d4e5f6a',
            userEmail: 'john.doe@example.com',
            category: 'BILLING',
            priority: 'HIGH',
            status: 'OPEN',
            subject: 'Unable to process payment for Premium renewal',
            body: 'My credit card was charged but my account still shows Standard plan.',
            createdAt: new Date().toISOString(),
          },
          {
            id: 'TCK-8802',
            accountId: '9b2f8d0e-4c3b-5e6f-0a1b-2c3d4e5f6a7b',
            userEmail: 'jane.smith@example.com',
            category: 'PLAYBACK',
            priority: 'MEDIUM',
            status: 'IN_PROGRESS',
            subject: 'Error 504 on Smart TV playback stream',
            body: 'When launching 4K playback on Samsung Smart TV, the stream buffers indefinitely.',
            createdAt: new Date(Date.now() - 86400000).toISOString(),
          },
        ];
        setTickets(mockTickets);
        setSelectedTicket(mockTickets[0]);
      }
    } catch (err) {
      console.error('Failed to load support tickets:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleResolveTicket = (ticketId: string) => {
    setTickets(
      tickets.map((t) => (t.id === ticketId ? { ...t, status: 'RESOLVED' } : t))
    );
    if (selectedTicket && selectedTicket.id === ticketId) {
      setSelectedTicket({ ...selectedTicket, status: 'RESOLVED' });
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-800 pb-4">
        <h3 className="text-lg font-bold text-white flex items-center gap-2">
          <LifeBuoy className="w-5 h-5 text-red-500" />
          Support Ticket Center & User Context Panel
        </h3>
        <p className="text-xs text-slate-400">Resolve customer inquiries with unified user account, subscription, and device contextual visibility.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Ticket List */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden divide-y divide-slate-800">
          <div className="p-4 bg-slate-950 text-xs font-bold text-white uppercase tracking-wider">
            Active Support Tickets ({tickets.length})
          </div>
          {tickets.map((ticket) => (
            <div
              key={ticket.id}
              onClick={() => setSelectedTicket(ticket)}
              className={`p-4 cursor-pointer transition ${
                selectedTicket?.id === ticket.id ? 'bg-slate-800 border-l-4 border-l-red-600' : 'hover:bg-slate-800/50'
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className="font-mono text-xs font-bold text-white">{ticket.id}</span>
                <StatusBadge status={ticket.status} />
              </div>
              <p className="text-xs font-semibold text-slate-200 truncate">{ticket.subject}</p>
              <p className="text-[11px] text-slate-400 mt-1">{ticket.userEmail}</p>
            </div>
          ))}
        </div>

        {/* Selected Ticket Detail & User Context */}
        {selectedTicket ? (
          <div className="lg:col-span-2 space-y-6">
            {/* Context Panel */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
                  <User className="w-4 h-4 text-blue-400" />
                  User Operational Context Panel
                </h4>
                <span className="text-xs font-mono text-slate-400">ID: {selectedTicket.accountId}</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
                <div className="bg-slate-950 border border-slate-800 p-3 rounded-lg">
                  <span className="text-slate-400 block text-[10px]">Subscriber Plan</span>
                  <span className="font-bold text-white text-sm">Premium 4K (v1)</span>
                </div>
                <div className="bg-slate-950 border border-slate-800 p-3 rounded-lg">
                  <span className="text-slate-400 block text-[10px]">Registered Devices</span>
                  <span className="font-bold text-emerald-400 text-sm">3 / 5 Active</span>
                </div>
                <div className="bg-slate-950 border border-slate-800 p-3 rounded-lg">
                  <span className="text-slate-400 block text-[10px]">Account Health</span>
                  <span className="font-bold text-emerald-400 text-sm">Verified / Active</span>
                </div>
              </div>
            </div>

            {/* Ticket Conversation */}
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div>
                  <h4 className="text-base font-bold text-white">{selectedTicket.subject}</h4>
                  <span className="text-xs text-slate-400">Category: {selectedTicket.category} | Priority: {selectedTicket.priority}</span>
                </div>
                {selectedTicket.status !== 'RESOLVED' && (
                  <button
                    onClick={() => handleResolveTicket(selectedTicket.id)}
                    className="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition"
                  >
                    <CheckCircle className="w-4 h-4" />
                    Mark Ticket Resolved
                  </button>
                )}
              </div>

              <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl space-y-2">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span className="font-semibold text-white">{selectedTicket.userEmail}</span>
                  <span>{new Date(selectedTicket.createdAt).toLocaleString()}</span>
                </div>
                <p className="text-xs text-slate-300 leading-relaxed">{selectedTicket.body}</p>
              </div>

              {/* Support Reply Box */}
              <div className="pt-2">
                <label className="block text-xs font-semibold text-slate-300 mb-1">Agent Internal Response Note</label>
                <textarea
                  rows={3}
                  className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
                  placeholder="Type support agent resolution response..."
                />
                <button
                  onClick={() => handleResolveTicket(selectedTicket.id)}
                  className="mt-3 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
                >
                  Send Agent Response & Update Ticket
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-xl p-8 text-center text-slate-500">
            Select a support ticket from the list to view user context details.
          </div>
        )}
      </div>
    </div>
  );
};
