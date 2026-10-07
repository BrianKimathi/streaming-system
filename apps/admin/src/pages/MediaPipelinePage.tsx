import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { MediaAsset } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { UploadCloud, FileVideo, ArrowLeft, Play, HardDrive } from 'lucide-react';

export const MediaPipelinePage: React.FC = () => {
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [contentId, setContentId] = useState('');
  const [filename, setFilename] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadAssets();
  }, []);

  const loadAssets = async () => {
    try {
      setLoading(true);
      const res = await adminService.getMediaAssets();
      if (res.data) setAssets(res.data);
    } catch (err) {
      console.error('Failed to load media assets:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleUploadSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await adminService.uploadMedia(contentId, filename);
      if (res.data) {
        setAssets([res.data, ...assets]);
        setIsUploading(false);
        setContentId('');
        setFilename('');
      }
    } catch (err) {
      console.error('Failed to trigger upload:', err);
    }
  };

  if (isUploading) {
    return (
      <div className="space-y-6 max-w-2xl mx-auto">
        <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
          <button
            onClick={() => setIsUploading(false)}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-300 hover:text-white hover:bg-slate-800 transition"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h3 className="text-lg font-bold text-white">Trigger HLS Video Ingestion Pipeline</h3>
            <p className="text-xs text-slate-400">Initiate FFmpeg adaptive bitrate HLS playlist generation.</p>
          </div>
        </div>

        <form onSubmit={handleUploadSubmit} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Target Catalog Content ID (UUID)</label>
            <input
              type="text"
              required
              value={contentId}
              onChange={(e) => setContentId(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              placeholder="e.g. 5ac1d6b1-0618-4b42-8bc6-42ad1961d54f"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Source Video Filename</label>
            <input
              type="text"
              required
              value={filename}
              onChange={(e) => setFilename(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white placeholder-slate-500 focus:outline-none focus:border-red-500"
              placeholder="e.g. blockbuster_4k_master.mp4"
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={() => setIsUploading(false)}
              className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
            >
              Start Transcoding
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
          <h3 className="text-lg font-bold text-white">HLS Media Pipeline & Transcoding</h3>
          <p className="text-xs text-slate-400">Monitor adaptive bitrate master `.m3u8` playlists and media file encoding status.</p>
        </div>
        <button
          onClick={() => setIsUploading(true)}
          className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
        >
          <UploadCloud className="w-4 h-4" />
          Ingest Video File Page
        </button>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
            <tr>
              <th className="px-6 py-4">Source File & Content ID</th>
              <th className="px-6 py-4">Master HLS Playlist</th>
              <th className="px-6 py-4">Transcoding Status</th>
              <th className="px-6 py-4">Duration</th>
              <th className="px-6 py-4">Uploaded At</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {assets.map((asset) => (
              <tr key={asset.id} className="hover:bg-slate-800/40 transition">
                <td className="px-6 py-4">
                  <div className="flex items-center gap-2">
                    <FileVideo className="w-4 h-4 text-red-500" />
                    <div>
                      <span className="font-semibold text-white block">{asset.filename}</span>
                      <span className="text-[10px] text-slate-500 font-mono">{asset.contentId}</span>
                    </div>
                  </div>
                </td>
                <td className="px-6 py-4 font-mono text-[11px] text-slate-400">
                  {asset.masterPlaylistUrl || `/api/v1/media/${asset.id}/hls/master.m3u8`}
                </td>
                <td className="px-6 py-4">
                  <StatusBadge status={asset.transcodingStatus || 'COMPLETED'} />
                </td>
                <td className="px-6 py-4 text-slate-400">
                  {Math.floor((asset.durationSeconds || 7200) / 60)} mins
                </td>
                <td className="px-6 py-4 text-slate-400">
                  {asset.uploadedAt ? new Date(asset.uploadedAt).toLocaleDateString() : 'Today'}
                </td>
              </tr>
            ))}
            {assets.length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-slate-500">
                  No video assets ingested yet. Click "Ingest Video File Page" above to begin.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
