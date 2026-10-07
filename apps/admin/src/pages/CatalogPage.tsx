import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { Movie, TVShow, Genre } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { Plus, Film, Tv, Clock } from 'lucide-react';
import { CreateMoviePage } from './CreateMoviePage';
import { CreateTvShowPage } from './CreateTvShowPage';

export const CatalogPage: React.FC = () => {
  const [activeSubTab, setActiveSubTab] = useState<'movies' | 'tvshows'>('movies');
  const [viewMode, setViewMode] = useState<'list' | 'create-movie' | 'create-tvshow'>('list');
  const [movies, setMovies] = useState<Movie[]>([]);
  const [tvShows, setTvShows] = useState<TVShow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadCatalogData();
  }, []);

  const loadCatalogData = async () => {
    try {
      setLoading(true);
      const [moviesRes, tvRes] = await Promise.all([
        adminService.getMovies(),
        adminService.getTVShows(),
      ]);

      if (moviesRes.data) setMovies(moviesRes.data);
      if (tvRes.data) setTvShows(tvRes.data);
    } catch (err) {
      console.error('Failed to load catalog data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleMovieStatus = async (movie: Movie) => {
    const nextStatus = movie.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED';
    try {
      await adminService.updateMovieStatus(movie.id, nextStatus);
      setMovies(movies.map((m) => (m.id === movie.id ? { ...m, status: nextStatus } : m)));
    } catch (err) {
      setMovies(movies.map((m) => (m.id === movie.id ? { ...m, status: nextStatus } : m)));
    }
  };

  if (viewMode === 'create-movie') {
    return <CreateMoviePage onBack={() => { setViewMode('list'); loadCatalogData(); }} />;
  }

  if (viewMode === 'create-tvshow') {
    return <CreateTvShowPage onBack={() => { setViewMode('list'); loadCatalogData(); }} />;
  }

  return (
    <div className="space-y-6">
      {/* Sub Tabs Header */}
      <div className="flex items-center justify-between border-b border-slate-800 pb-4">
        <div className="flex gap-2">
          <button
            onClick={() => setActiveSubTab('movies')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition ${
              activeSubTab === 'movies'
                ? 'bg-red-600 text-white'
                : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800'
            }`}
          >
            <Film className="w-4 h-4" />
            Movies ({movies.length})
          </button>
          <button
            onClick={() => setActiveSubTab('tvshows')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition ${
              activeSubTab === 'tvshows'
                ? 'bg-red-600 text-white'
                : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800'
            }`}
          >
            <Tv className="w-4 h-4" />
            TV Shows ({tvShows.length})
          </button>
        </div>

        <div>
          {activeSubTab === 'movies' ? (
            <button
              onClick={() => setViewMode('create-movie')}
              className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
            >
              <Plus className="w-4 h-4" />
              Add Movie Page
            </button>
          ) : (
            <button
              onClick={() => setViewMode('create-tvshow')}
              className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
            >
              <Plus className="w-4 h-4" />
              Add TV Show Page
            </button>
          )}
        </div>
      </div>

      {/* Movies Table */}
      {activeSubTab === 'movies' && (
        <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider">
              <tr>
                <th className="px-6 py-4">Title</th>
                <th className="px-6 py-4">Release Year</th>
                <th className="px-6 py-4">Duration</th>
                <th className="px-6 py-4">Rating</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {movies.map((movie) => (
                <tr key={movie.id} className="hover:bg-slate-800/40 transition">
                  <td className="px-6 py-4 font-semibold text-white">{movie.title}</td>
                  <td className="px-6 py-4">{movie.releaseYear}</td>
                  <td className="px-6 py-4 flex items-center gap-1 text-slate-400">
                    <Clock className="w-3.5 h-3.5" />
                    {movie.durationMinutes} mins
                  </td>
                  <td className="px-6 py-4 font-medium text-slate-400">{movie.maturityRating}</td>
                  <td className="px-6 py-4">
                    <StatusBadge status={movie.status} />
                  </td>
                  <td className="px-6 py-4 text-right">
                    <button
                      onClick={() => handleToggleMovieStatus(movie)}
                      className="px-3 py-1 rounded border border-slate-700 text-slate-300 hover:bg-slate-800 text-[11px] font-semibold transition"
                    >
                      Toggle {movie.status === 'PUBLISHED' ? 'Unpublish' : 'Publish'}
                    </button>
                  </td>
                </tr>
              ))}
              {movies.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                    No movies found in catalog. Click "Add Movie Page" above to create one.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* TV Shows Table */}
      {activeSubTab === 'tvshows' && (
        <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider">
              <tr>
                <th className="px-6 py-4">Series Title</th>
                <th className="px-6 py-4">Release Year</th>
                <th className="px-6 py-4">Seasons Count</th>
                <th className="px-6 py-4">Rating</th>
                <th className="px-6 py-4">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {tvShows.map((show) => (
                <tr key={show.id} className="hover:bg-slate-800/40 transition">
                  <td className="px-6 py-4 font-semibold text-white">{show.title}</td>
                  <td className="px-6 py-4">{show.releaseYear}</td>
                  <td className="px-6 py-4">{show.seasonsCount} Season(s)</td>
                  <td className="px-6 py-4 text-slate-400">{show.maturityRating}</td>
                  <td className="px-6 py-4">
                    <StatusBadge status={show.status} />
                  </td>
                </tr>
              ))}
              {tvShows.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-slate-500">
                    No TV shows found in catalog. Click "Add TV Show Page" above to create one.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};
