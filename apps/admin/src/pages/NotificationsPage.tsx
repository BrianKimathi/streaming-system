import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { NotificationLog } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { Bell, Mail, MessageSquare, Send, ArrowLeft, CheckCircle } from 'lucide-react';

export const NotificationsPage: React.FC = () => {
  const [logs, setLogs] = useState<NotificationLog[]>([]);
  const [isDispatching, setIsDispatching] = useState(false);
  const [accountId, setAccountId] = useState('');
  const [recipient, setRecipient] = useState('');
  const [channel, setChannel] = useState<'EMAIL' | 'SMS'>('EMAIL');
  const [template, setTemplate] = useState('WELCOME');
  const [subject, setSubject] = useState('Welcome to StreamX Premium');
  const [body, setBody] = useState('Your StreamX subscription is now active.');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadNotifications();
  }, []);

  const loadNotifications = async () => {
    try {
      setLoading(true);
      const res = await adminService.getUserNotifications();
      if (res.data) setLogs(res.data);
    } catch (err) {
      console.error('Failed to load notifications:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSendNotification = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await adminService.sendNotification({
        accountId,
        recipient,
        channel,
        template,
        subject,
        body,
      });
      if (res.data) {
        setLogs([res.data, ...logs]);
        setSuccessMsg('Notification successfully dispatched to recipient!');
        setTimeout(() => {
          setSuccessMsg('');
          setIsDispatching(false);
        }, 1200);
      }
    } catch (err) {
      console.error('Failed to send notification:', err);
    }
  };

  if (isDispatching) {
    return (
      <div className="space-y-6 max-w-2xl mx-auto">
        <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
          <button
            onClick={() => setIsDispatching(false)}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h3 className="text-lg font-bold text-white">Dispatch Multi-Channel Notification Page</h3>
            <p className="text-xs text-slate-400">Trigger email or SMS alerts to subscribers.</p>
          </div>
        </div>

        {successMsg && (
          <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 p-4 rounded-xl text-xs font-semibold flex items-center gap-2">
            <CheckCircle className="w-4 h-4" />
            {successMsg}
          </div>
        )}

        <form onSubmit={handleSendNotification} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Target Account ID (UUID)</label>
            <input
              type="text"
              required
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              placeholder="e.g. 8a1f7c9e-3b2a-4d5e-9f0a-1b2c3d4e5f6a"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Recipient Address / Phone</label>
              <input
                type="text"
                required
                value={recipient}
                onChange={(e) => setRecipient(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
                placeholder="user@example.com or +15550000000"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Channel</label>
              <select
                value={channel}
                onChange={(e) => setChannel(e.target.value as 'EMAIL' | 'SMS')}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              >
                <option value="EMAIL">Email Delivery</option>
                <option value="SMS">SMS Message</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Subject Header</label>
            <input
              type="text"
              required
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Notification Body Text</label>
            <textarea
              required
              rows={3}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={() => setIsDispatching(false)}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
            >
              Dispatch Notification
            </button>
          </div>
        </form>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">Notifications Audit Log</h3>
          <p className="text-xs text-slate-400">Track multi-channel email, SMS, and OTP notification logs.</p>
        </div>
        <button
          onClick={() => setIsDispatching(true)}
          className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
        >
          <Send className="w-4 h-4" />
          Send Notification Page
        </button>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
            <tr>
              <th className="px-6 py-4">Recipient</th>
              <th className="px-6 py-4">Channel & Template</th>
              <th className="px-6 py-4">Subject</th>
              <th className="px-6 py-4">Status</th>
              <th className="px-6 py-4">Dispatched Date</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {logs.map((log) => (
              <tr key={log.id} className="hover:bg-slate-800/40 transition">
                <td className="px-6 py-4 font-semibold text-white">{log.recipient}</td>
                <td className="px-6 py-4">
                  <div className="flex items-center gap-1.5">
                    {log.channel === 'SMS' ? <MessageSquare className="w-3.5 h-3.5 text-slate-400" /> : <Mail className="w-3.5 h-3.5 text-slate-400" />}
                    <span className="font-medium text-slate-300">{log.channel}</span>
                    <span className="text-slate-500">({log.template})</span>
                  </div>
                </td>
                <td className="px-6 py-4 text-slate-300">{log.subject}</td>
                <td className="px-6 py-4">
                  <StatusBadge status={log.status} />
                </td>
                <td className="px-6 py-4 text-slate-400">
                  {log.createdAt ? new Date(log.createdAt).toLocaleString() : 'Recently'}
                </td>
              </tr>
            ))}
            {logs.length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-slate-500">
                  No notification logs found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
