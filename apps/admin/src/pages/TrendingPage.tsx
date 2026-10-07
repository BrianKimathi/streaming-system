import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { TrendingItem } from '../types';
import { TrendingUp, Flame, Eye, CheckCircle2, ThumbsUp, Zap } from 'lucide-react';

export const TrendingPage: React.FC = () => {
  const [trending, setTrending] = useState<TrendingItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadTrending();
  }, []);

  const loadTrending = async () => {
    try {
      setLoading(true);
      const res = await adminService.getTopTrending(20);
      if (res.data) setTrending(res.data);
    } catch (err) {
      console.error('Failed to load trending items:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSimulateEvent = async (contentId: string, title: string, eventType: 'VIEW' | 'COMPLETION' | 'LIKE') => {
    try {
      await adminService.recordTrendingEvent({
        contentId,
        title,
        contentType: 'MOVIE',
        eventType,
      });
      loadTrending();
    } catch (err) {
      console.error('Failed to record event:', err);
    }
  };

  return (
    <div className="space-y-6">
      {/* Velocity Scoring Header Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-white mb-1 flex items-center gap-2">
            <Flame className="w-5 h-5 text-red-500" />
            Content Velocity Engine
          </h3>
          <p className="text-xs text-slate-400 font-mono">
            Score = (5 × Views 1h) + (3 × Views 6h) + (2 × Completions 24h) + (1 × Likes 24h)
          </p>
        </div>
        <button
          onClick={loadTrending}
          className="px-3.5 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
        >
          Re-Calculate Velocity Rankings
        </button>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
            <tr>
              <th className="px-6 py-4">Rank & Title</th>
              <th className="px-6 py-4">1h Views</th>
              <th className="px-6 py-4">6h Views</th>
              <th className="px-6 py-4">24h Completions</th>
              <th className="px-6 py-4">24h Likes</th>
              <th className="px-6 py-4">Velocity Score</th>
              <th className="px-6 py-4 text-right">Event Simulation</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {trending.map((item, index) => (
              <tr key={item.contentId} className="hover:bg-slate-800/40 transition">
                <td className="px-6 py-4 font-semibold text-white flex items-center gap-3">
                  <span className="w-6 h-6 rounded bg-slate-800 border border-slate-700 text-[11px] font-bold text-slate-300 flex items-center justify-center">
                    #{index + 1}
                  </span>
                  <span>{item.title}</span>
                </td>
                <td className="px-6 py-4 text-slate-300">{item.views1h}</td>
                <td className="px-6 py-4 text-slate-300">{item.views6h}</td>
                <td className="px-6 py-4 text-slate-300">{item.completions24h}</td>
                <td className="px-6 py-4 text-slate-300">{item.likes24h}</td>
                <td className="px-6 py-4 font-bold text-red-400 text-sm">{item.velocityScore}</td>
                <td className="px-6 py-4 text-right">
                  <div className="flex items-center justify-end gap-1.5">
                    <button
                      onClick={() => handleSimulateEvent(item.contentId, item.title, 'VIEW')}
                      title="Simulate View Event (+8 pts)"
                      className="p-1.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
                    >
                      <Eye className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleSimulateEvent(item.contentId, item.title, 'COMPLETION')}
                      title="Simulate Completion Event (+2 pts)"
                      className="p-1.5 rounded bg-slate-800 hover:bg-slate-700 text-emerald-400 transition"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleSimulateEvent(item.contentId, item.title, 'LIKE')}
                      title="Simulate Like Event (+1 pt)"
                      className="p-1.5 rounded bg-slate-800 hover:bg-slate-700 text-amber-400 transition"
                    >
                      <ThumbsUp className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {trending.length === 0 && (
              <tr>
                <td colSpan={7} className="px-6 py-8 text-center text-slate-500">
                  No trending items currently ranked.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
