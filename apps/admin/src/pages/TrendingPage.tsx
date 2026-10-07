import React, { useCallback, useEffect, useState } from 'react';
import { Flame, RefreshCw } from 'lucide-react';
import { adminService } from '../services/adminService';
import { errorMessage } from '../api/client';
import type { TrendingItem } from '../types';
import { EmptyState, ErrorBanner, LoadingState } from '../components/common/Feedback';
import { formatDateTime, formatNumber } from '../utils/format';

const LIMIT = 50;

export const TrendingPage: React.FC = () => {
  const [trending, setTrending] = useState<TrendingItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadedAt, setLoadedAt] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTrending(await adminService.getTopTrending(LIMIT));
      setLoadedAt(new Date().toISOString());
    } catch (err) {
      setError(errorMessage(err, 'Failed to load trending leaderboard'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="space-y-6">
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-bold text-white mb-1 flex items-center gap-2">
            <Flame className="w-5 h-5 text-red-500" />
            Content Velocity Leaderboard
          </h3>
          <p className="text-xs text-slate-400 font-mono">
            Score = (5 × Views 1h) + (3 × Views 6h) + (2 × Completions 24h) + (1 × Likes 24h)
          </p>
          <p className="text-[11px] text-slate-500 mt-1">
            Read-only. Counters are recorded by the player apps. Top {LIMIT} titles
            {loadedAt ? `, fetched ${formatDateTime(loadedAt)}` : ''}.
          </p>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="flex items-center gap-2 px-3.5 py-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <ErrorBanner message={error} onRetry={load} />

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        {loading && trending.length === 0 ? (
          <LoadingState label="Loading leaderboard…" />
        ) : trending.length === 0 ? (
          error ? (
            <EmptyState title="Leaderboard unavailable" hint="The trending service could not be reached. See the error above." />
          ) : (
            <EmptyState
              title="No trending activity yet"
              hint="Counters are populated by the player apps when viewers watch, finish, or like titles. Nothing has been recorded yet."
            />
          )
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
                <tr>
                  <th className="px-6 py-4">Rank & Title</th>
                  <th className="px-6 py-4">Type</th>
                  <th className="px-6 py-4">1h Views</th>
                  <th className="px-6 py-4">6h Views</th>
                  <th className="px-6 py-4">24h Completions</th>
                  <th className="px-6 py-4">24h Likes</th>
                  <th className="px-6 py-4">Velocity Score</th>
                  <th className="px-6 py-4">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {trending.map((item, index) => (
                  <tr key={item.contentId} className="hover:bg-slate-800/40 transition">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <span className="w-7 h-6 rounded bg-slate-800 border border-slate-700 text-[11px] font-bold text-slate-300 flex items-center justify-center shrink-0">
                          #{index + 1}
                        </span>
                        <div className="min-w-0">
                          <p className="font-semibold text-white truncate">{item.title}</p>
                          <p className="text-[10px] text-slate-500 font-mono">{item.contentId}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-slate-400">{item.contentType}</td>
                    <td className="px-6 py-4">{formatNumber(item.views1h)}</td>
                    <td className="px-6 py-4">{formatNumber(item.views6h)}</td>
                    <td className="px-6 py-4">{formatNumber(item.completions24h)}</td>
                    <td className="px-6 py-4">{formatNumber(item.likes24h)}</td>
                    <td className="px-6 py-4 font-bold text-red-400 text-sm">{formatNumber(item.velocityScore)}</td>
                    <td className="px-6 py-4 text-slate-400 whitespace-nowrap">{formatDateTime(item.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
