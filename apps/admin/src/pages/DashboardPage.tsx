import React, { useEffect, useState } from 'react';
import { StatCard } from '../components/common/StatCard';
import { Users, Clock, DollarSign, PlayCircle, Activity, Award } from 'lucide-react';
import { adminService } from '../services/adminService';
import { AnalyticsDashboard } from '../types';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';

export const DashboardPage: React.FC = () => {
  const [analytics, setAnalytics] = useState<AnalyticsDashboard | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    try {
      setLoading(true);
      const res = await adminService.getDashboardAnalytics();
      if (res.success && res.data) {
        setAnalytics(res.data);
      }
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
    } finally {
      setLoading(false);
    }
  };

  const trendData = [
    { time: '00:00', users: 320, watchTime: 120 },
    { time: '04:00', users: 180, watchTime: 80 },
    { time: '08:00', users: 540, watchTime: 290 },
    { time: '12:00', users: 890, watchTime: 450 },
    { time: '16:00', users: 1120, watchTime: 680 },
    { time: '20:00', users: 1450, watchTime: 920 },
    { time: '23:59', users: 1250, watchTime: 810 },
  ];

  return (
    <div className="space-y-6">
      {/* Header Banner - Flat Theme */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-white mb-1">Executive Control Center</h3>
          <p className="text-xs text-slate-400">Real-time microservices performance, active stream metrics, and financial summaries.</p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400">Date: <strong className="text-white">{analytics?.date || new Date().toISOString().split('T')[0]}</strong></span>
          <button
            onClick={loadDashboardData}
            className="px-3.5 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
          >
            Refresh Metrics
          </button>
        </div>
      </div>

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard
          title="Daily Active Users (DAU)"
          value={analytics?.dailyActiveUsers.toLocaleString() || '1,250'}
          change="12.5%"
          isPositive={true}
          icon={Users}
          iconBgColor="bg-slate-800 text-blue-400"
        />
        <StatCard
          title="Monthly Active Users (MAU)"
          value={analytics?.monthlyActiveUsers.toLocaleString() || '15,400'}
          change="8.2%"
          isPositive={true}
          icon={Activity}
          iconBgColor="bg-slate-800 text-indigo-400"
        />
        <StatCard
          title="Total Watch Time (Hours)"
          value={`${analytics?.totalWatchTimeHours.toLocaleString() || '1,250'} hrs`}
          change="15.8%"
          isPositive={true}
          icon={Clock}
          iconBgColor="bg-slate-800 text-emerald-400"
        />
        <StatCard
          title="Average Completion Rate"
          value={`${analytics?.averageCompletionRatePercentage || '78.5'}%`}
          change="4.1%"
          isPositive={true}
          icon={Award}
          iconBgColor="bg-slate-800 text-amber-400"
        />
        <StatCard
          title="Active Subscriptions"
          value={analytics?.totalSubscriptionsActive.toLocaleString() || '14,200'}
          change="6.4%"
          isPositive={true}
          icon={PlayCircle}
          iconBgColor="bg-slate-800 text-purple-400"
        />
        <StatCard
          title="Platform Revenue (USD)"
          value={`$${analytics?.totalRevenue.toLocaleString() || '141,858'}`}
          change="11.3%"
          isPositive={true}
          icon={DollarSign}
          iconBgColor="bg-slate-800 text-emerald-400"
        />
      </div>

      {/* Flat Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h4 className="text-xs font-bold text-white uppercase tracking-wider">Active Concurrent Viewers</h4>
            <span className="text-xs text-slate-400">24-Hour Analytics</span>
          </div>
          <div className="h-60">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trendData}>
                <XAxis dataKey="time" stroke="#64748b" fontSize={11} />
                <YAxis stroke="#64748b" fontSize={11} />
                <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155' }} />
                <Area type="monotone" dataKey="users" stroke="#dc2626" fill="#1e293b" fillOpacity={0.6} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <h4 className="text-xs font-bold text-white uppercase tracking-wider">Watch Time Bandwidth (GB/s)</h4>
            <span className="text-xs text-slate-400">Network Consumption</span>
          </div>
          <div className="h-60">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trendData}>
                <XAxis dataKey="time" stroke="#64748b" fontSize={11} />
                <YAxis stroke="#64748b" fontSize={11} />
                <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155' }} />
                <Area type="monotone" dataKey="watchTime" stroke="#6366f1" fill="#1e293b" fillOpacity={0.6} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  );
};
