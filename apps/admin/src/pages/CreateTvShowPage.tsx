import React, { useState } from 'react';
import { adminService } from '../services/adminService';
import { ArrowLeft, CheckCircle } from 'lucide-react';

interface CreateTvShowPageProps {
  onBack: () => void;
}

export const CreateTvShowPage: React.FC<CreateTvShowPageProps> = ({ onBack }) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [releaseYear, setReleaseYear] = useState(2026);
  const [maturityRating, setMaturityRating] = useState('TV-MA');
  const [seasonsCount, setSeasonsCount] = useState(1);
  const [status, setStatus] = useState<'PUBLISHED' | 'DRAFT'>('PUBLISHED');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setIsSubmitting(true);
      await adminService.createTVShow({
        title,
        description,
        releaseYear,
        maturityRating,
        seasonsCount,
        status,
        posterUrl: 'https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?auto=format&fit=crop&w=600&q=80',
        bannerUrl: 'https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?auto=format&fit=crop&w=1200&q=80',
      });
      setSuccessMessage('TV Show successfully created!');
      setTimeout(() => {
        onBack();
      }, 1500);
    } catch (err) {
      console.error('Failed to create TV show:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex items-center justify-between border-b border-slate-800 pb-4">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h3 className="text-lg font-bold text-white">Create New TV Show</h3>
            <p className="text-xs text-slate-400">Add a multi-season TV show entry to the StreamX catalog.</p>
          </div>
        </div>
      </div>

      {successMessage && (
        <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 p-4 rounded-xl text-xs font-semibold flex items-center gap-2">
          <CheckCircle className="w-4 h-4" />
          {successMessage}
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
        <div className="space-y-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Series Information</h4>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Show Title</label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              placeholder="e.g. Stranger Things"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Series Overview</label>
            <textarea
              required
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              placeholder="Synopsis of the TV series plot..."
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Release Year</label>
              <input
                type="number"
                required
                value={releaseYear}
                onChange={(e) => setReleaseYear(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Initial Seasons Count</label>
              <input
                type="number"
                required
                value={seasonsCount}
                onChange={(e) => setSeasonsCount(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Maturity Rating</label>
              <select
                value={maturityRating}
                onChange={(e) => setMaturityRating(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              >
                <option value="TV-Y">TV-Y - All Children</option>
                <option value="TV-PG">TV-PG - Parental Guidance</option>
                <option value="TV-14">TV-14 - Parents Strongly Cautioned</option>
                <option value="TV-MA">TV-MA - Mature Audience Only</option>
              </select>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-end gap-3 border-t border-slate-800 pt-4">
          <button
            type="button"
            onClick={onBack}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {isSubmitting ? 'Creating...' : 'Save & Create TV Show'}
          </button>
        </div>
      </form>
    </div>
  );
};
