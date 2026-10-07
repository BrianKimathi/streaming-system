import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { Genre } from '../types';
import { Tag, Plus, ArrowLeft, CheckCircle } from 'lucide-react';

export const GenresPage: React.FC = () => {
  const [genres, setGenres] = useState<Genre[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(true);
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    loadGenres();
  }, []);

  const loadGenres = async () => {
    try {
      setLoading(true);
      const res = await adminService.getGenres();
      if (res.data) setGenres(res.data);
    } catch (err) {
      console.error('Failed to load genres:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateGenre = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await adminService.createGenre(name, description);
      if (res.data) {
        setGenres([...genres, res.data]);
        setSuccessMessage('Genre created successfully!');
        setTimeout(() => {
          setSuccessMessage('');
          setIsCreating(false);
          setName('');
          setDescription('');
        }, 1200);
      }
    } catch (err) {
      console.error('Failed to create genre:', err);
    }
  };

  if (isCreating) {
    return (
      <div className="space-y-6 max-w-2xl mx-auto">
        <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
          <button
            onClick={() => setIsCreating(false)}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h3 className="text-lg font-bold text-white">Create New Genre Category</h3>
            <p className="text-xs text-slate-400">Define a new category tag for grouping catalog titles.</p>
          </div>
        </div>

        {successMessage && (
          <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 p-4 rounded-xl text-xs font-semibold flex items-center gap-2">
            <CheckCircle className="w-4 h-4" />
            {successMessage}
          </div>
        )}

        <form onSubmit={handleCreateGenre} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Genre Name</label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              placeholder="e.g. Cyberpunk, Psychological Thriller, Anime"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Category Description</label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              placeholder="Description of content belonging to this genre..."
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={() => setIsCreating(false)}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
            >
              Save Genre
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
          <h3 className="text-lg font-bold text-white">Genre Taxonomy</h3>
          <p className="text-xs text-slate-400">Manage catalog content classification genres.</p>
        </div>
        <button
          onClick={() => setIsCreating(true)}
          className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
        >
          <Plus className="w-4 h-4" />
          Create Genre Page
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {genres.map((g) => (
          <div key={g.id} className="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <h4 className="font-bold text-white mb-1 flex items-center gap-2 text-sm">
              <Tag className="w-4 h-4 text-red-500" />
              {g.name}
            </h4>
            <p className="text-xs text-slate-400">{g.description || 'Standard catalog category.'}</p>
          </div>
        ))}
      </div>
    </div>
  );
};
