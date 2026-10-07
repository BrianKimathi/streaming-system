import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle2, Film, ListVideo, Plus, RefreshCw, Search, Tag, Tv } from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import { CONTENT_STATUSES } from '../types';
import type { CatalogStats, ContentStatus, Genre, Movie, TVShow } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { StatCard } from '../components/common/StatCard';
import { EmptyState, ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDate, formatDateTime, formatNumber } from '../utils/format';
import { CreateMoviePage } from './CreateMoviePage';
import { CreateTvShowPage } from './CreateTvShowPage';
import { TvShowEpisodesPage } from './TvShowEpisodesPage';

type SubTab = 'movies' | 'tvshows';
type ViewMode = 'list' | 'create-movie' | 'create-tvshow' | 'manage-episodes';

interface CatalogRow {
  id: string;
  title: string;
  posterUrl: string | null;
  genres: Genre[];
  releaseDate: string | null;
  length: string;
  status: ContentStatus;
  createdAt: string | null;
}

function movieRow(movie: Movie): CatalogRow {
  return {
    id: movie.id,
    title: movie.title,
    posterUrl: movie.posterUrl,
    genres: movie.genres ?? [],
    releaseDate: movie.releaseDate,
    length: movie.runtimeMinutes != null ? `${movie.runtimeMinutes} min` : '—',
    status: movie.status,
    createdAt: movie.createdAt,
  };
}

function showRow(show: TVShow): CatalogRow {
  return {
    id: show.id,
    title: show.title,
    posterUrl: show.posterUrl,
    genres: show.genres ?? [],
    releaseDate: show.releaseDate,
    length: `${show.seasonsCount} season${show.seasonsCount === 1 ? '' : 's'}`,
    status: show.status,
    createdAt: show.createdAt,
  };
}

export const CatalogPage: React.FC = () => {
  const [activeSubTab, setActiveSubTab] = useState<SubTab>('movies');
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [movies, setMovies] = useState<Movie[]>([]);
  const [tvShows, setTvShows] = useState<TVShow[]>([]);
  const [stats, setStats] = useState<CatalogStats | null>(null);
  const [statsError, setStatsError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [pendingIds, setPendingIds] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<ContentStatus | ''>('');
  const [managedShow, setManagedShow] = useState<TVShow | null>(null);

  const loadStats = useCallback(async () => {
    try {
      setStats(await adminService.getCatalogStats());
      setStatsError(null);
    } catch (err) {
      setStatsError(errorMessage(err, 'Failed to load catalog stats'));
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [movieList, showList] = await Promise.all([adminService.getMovies(), adminService.getTVShows()]);
      setMovies(movieList);
      setTvShows(showList);
    } catch (err) {
      setError(errorMessage(err, 'Failed to load catalog'));
    } finally {
      setLoading(false);
    }
    await loadStats();
  }, [loadStats]);

  useEffect(() => {
    load();
  }, [load]);

  const handleStatusChange = async (type: SubTab, id: string, oldStatus: ContentStatus, newStatus: ContentStatus) => {
    if (oldStatus === newStatus) return;
    setPendingIds((ids) => [...ids, id]);
    setActionError(null);
    setSuccess(null);
    try {
      let title: string;
      let confirmed: ContentStatus;
      if (type === 'movies') {
        const updated = await adminService.updateMovieStatus(id, newStatus);
        setMovies((list) => list.map((m) => (m.id === id ? updated : m)));
        title = updated.title;
        confirmed = updated.status;
      } else {
        const updated = await adminService.updateTVShowStatus(id, newStatus);
        setTvShows((list) => list.map((s) => (s.id === id ? updated : s)));
        title = updated.title;
        confirmed = updated.status;
      }
      audit({
        action: 'CONTENT_STATUS_CHANGED',
        targetType: type === 'movies' ? 'MOVIE' : 'TV_SHOW',
        targetId: id,
        details: `${oldStatus} -> ${confirmed}`,
      });
      setSuccess(`"${title}" is now ${confirmed}.`);
      loadStats();
    } catch (err) {
      setActionError(errorMessage(err, 'Failed to update status'));
    } finally {
      setPendingIds((ids) => ids.filter((x) => x !== id));
    }
  };

  const rows = useMemo(() => {
    const source = activeSubTab === 'movies' ? movies.map(movieRow) : tvShows.map(showRow);
    const query = search.trim().toLowerCase();
    return source.filter(
      (row) =>
        (!statusFilter || row.status === statusFilter) &&
        (!query ||
          row.title.toLowerCase().includes(query) ||
          row.genres.some((g) => g.name.toLowerCase().includes(query)))
    );
  }, [activeSubTab, movies, tvShows, search, statusFilter]);

  if (viewMode === 'create-movie') {
    return (
      <CreateMoviePage
        onCancel={() => setViewMode('list')}
        onDone={(movie) => {
          setMovies((list) => [movie, ...list.filter((m) => m.id !== movie.id)]);
          setActiveSubTab('movies');
          setViewMode('list');
          setActionError(null);
          setSuccess(`Movie "${movie.title}" created with status ${movie.status}.`);
          loadStats();
        }}
      />
    );
  }

  if (viewMode === 'create-tvshow') {
    return (
      <CreateTvShowPage
        onCancel={() => setViewMode('list')}
        onDone={(show) => {
          setTvShows((list) => [show, ...list.filter((s) => s.id !== show.id)]);
          setActiveSubTab('tvshows');
          setViewMode('list');
          setActionError(null);
          setSuccess(`TV show "${show.title}" created with ${show.seasonsCount} season(s), status ${show.status}.`);
          loadStats();
        }}
      />
    );
  }

  if (viewMode === 'manage-episodes' && managedShow) {
    return (
      <TvShowEpisodesPage
        show={managedShow}
        onBack={() => {
          setManagedShow(null);
          setViewMode('list');
          setActiveSubTab('tvshows');
          load();
        }}
      />
    );
  }

  const isMovies = activeSubTab === 'movies';
  const totalForTab = isMovies ? movies.length : tvShows.length;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Movies"
          value={formatNumber(stats?.totalMovies)}
          subtitle={stats ? `${formatNumber(stats.publishedMovies)} published` : statsError ?? undefined}
          icon={Film}
          unavailable={!stats}
        />
        <StatCard
          title="TV Shows"
          value={formatNumber(stats?.totalTvShows)}
          subtitle={stats ? `${formatNumber(stats.publishedTvShows)} published` : statsError ?? undefined}
          icon={Tv}
          iconBgColor="bg-indigo-500/10 text-indigo-400"
          unavailable={!stats}
        />
        <StatCard
          title="Published Titles"
          value={stats ? formatNumber(stats.publishedMovies + stats.publishedTvShows) : '—'}
          subtitle="Visible in public apps"
          icon={CheckCircle2}
          iconBgColor="bg-emerald-500/10 text-emerald-400"
          unavailable={!stats}
        />
        <StatCard
          title="Genres"
          value={formatNumber(stats?.totalGenres)}
          icon={Tag}
          iconBgColor="bg-amber-500/10 text-amber-400"
          unavailable={!stats}
        />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
        <div className="flex gap-2">
          <button
            onClick={() => setActiveSubTab('movies')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition ${
              isMovies ? 'bg-red-600 text-white' : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800'
            }`}
          >
            <Film className="w-4 h-4" />
            Movies ({movies.length})
          </button>
          <button
            onClick={() => setActiveSubTab('tvshows')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition ${
              !isMovies ? 'bg-red-600 text-white' : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800'
            }`}
          >
            <Tv className="w-4 h-4" />
            TV Shows ({tvShows.length})
          </button>
        </div>

        <div className="flex gap-2">
          <button
            onClick={load}
            disabled={loading}
            className="flex items-center gap-2 px-3 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={() => setViewMode('create-movie')}
            className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
          >
            <Plus className="w-4 h-4" />
            Create Movie
          </button>
          <button
            onClick={() => setViewMode('create-tvshow')}
            className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
          >
            <Plus className="w-4 h-4" />
            Create TV Show
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-md">
          <Search className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by title or genre"
            className="w-full pl-9 pr-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as ContentStatus | '')}
          className="px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
        >
          <option value="">All statuses</option>
          {CONTENT_STATUSES.map((s) => (
            <option key={s} value={s}>
              {s.replace(/_/g, ' ')}
            </option>
          ))}
        </select>
      </div>

      <ErrorBanner message={error} onRetry={load} />
      <ErrorBanner message={actionError} />
      <SuccessBanner message={success} />

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        {loading && totalForTab === 0 ? (
          <LoadingState label="Loading catalog…" />
        ) : rows.length === 0 ? (
          error && totalForTab === 0 ? (
            <EmptyState title="Catalog unavailable" hint="The catalog could not be loaded. See the error above." />
          ) : totalForTab === 0 ? (
            <EmptyState
              title={isMovies ? 'No movies in the catalog yet' : 'No TV shows in the catalog yet'}
              hint={`Use "${isMovies ? 'Create Movie' : 'Create TV Show'}" to add the first title.`}
            />
          ) : (
            <EmptyState title="No titles match the current filters" />
          )
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-950 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider">
                <tr>
                  <th className="px-6 py-4">Title</th>
                  <th className="px-6 py-4">Genres</th>
                  <th className="px-6 py-4">Release Date</th>
                  <th className="px-6 py-4">{isMovies ? 'Runtime' : 'Seasons'}</th>
                  <th className="px-6 py-4">Status</th>
                  <th className="px-6 py-4">Created</th>
                  <th className="px-6 py-4 text-right">{isMovies ? 'Change Status' : 'Status & Episodes'}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {rows.map((row) => {
                  const pending = pendingIds.includes(row.id);
                  return (
                    <tr key={row.id} className="hover:bg-slate-800/40 transition">
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-3">
                          {row.posterUrl ? (
                            <img
                              src={row.posterUrl}
                              alt=""
                              className="w-9 h-12 rounded object-cover bg-slate-800 border border-slate-700 shrink-0"
                            />
                          ) : (
                            <div className="w-9 h-12 rounded bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0">
                              {isMovies ? <Film className="w-4 h-4 text-slate-600" /> : <Tv className="w-4 h-4 text-slate-600" />}
                            </div>
                          )}
                          <div className="min-w-0">
                            <p className="font-semibold text-white truncate">{row.title}</p>
                            <p className="text-[10px] text-slate-500 font-mono">{row.id}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-3">
                        {row.genres.length === 0 ? (
                          <span className="text-slate-600">—</span>
                        ) : (
                          <div className="flex flex-wrap gap-1">
                            {row.genres.map((g) => (
                              <span key={g.id} className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-[10px] text-slate-300">
                                {g.name}
                              </span>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-3 text-slate-400">{formatDate(row.releaseDate)}</td>
                      <td className="px-6 py-3 text-slate-400">{row.length}</td>
                      <td className="px-6 py-3">
                        <StatusBadge status={row.status} />
                      </td>
                      <td className="px-6 py-3 text-slate-400 whitespace-nowrap">{formatDateTime(row.createdAt)}</td>
                      <td className="px-6 py-3">
                        <div className="flex items-center justify-end gap-2">
                          <select
                            value={row.status}
                            disabled={pending}
                            onChange={(e) =>
                              handleStatusChange(activeSubTab, row.id, row.status, e.target.value as ContentStatus)
                            }
                            className="px-2 py-1 bg-slate-950 border border-slate-700 rounded text-[11px] text-slate-200 focus:outline-none focus:border-red-500 disabled:opacity-50"
                          >
                            {CONTENT_STATUSES.map((s) => (
                              <option key={s} value={s}>
                                {s.replace(/_/g, ' ')}
                              </option>
                            ))}
                          </select>
                          {!isMovies && (
                            <button
                              onClick={() => {
                                const show = tvShows.find((s) => s.id === row.id);
                                if (!show) return;
                                setActionError(null);
                                setSuccess(null);
                                setManagedShow(show);
                                setViewMode('manage-episodes');
                              }}
                              disabled={pending}
                              className="flex items-center gap-1.5 px-2.5 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-800 text-[11px] font-semibold whitespace-nowrap transition disabled:opacity-50"
                            >
                              <ListVideo className="w-3 h-3" />
                              Manage episodes
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
