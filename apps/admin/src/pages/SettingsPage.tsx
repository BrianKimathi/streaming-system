import React, { useState } from 'react';
import { Sliders, Shield, Key, HardDrive, CheckCircle, Database, RefreshCw } from 'lucide-react';

export const SettingsPage: React.FC = () => {
  const [jwtSecret, setJwtSecret] = useState('9a6f8b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a');
  const [accessTokenExpirationMs, setAccessTokenExpirationMs] = useState(900000); // 15 mins
  const [refreshTokenExpirationMs, setRefreshTokenExpirationMs] = useState(604800000); // 7 days
  const [hlsSegmentDurationSeconds, setHlsSegmentDurationSeconds] = useState(6);
  const [maxConcurrentStreamLimit, setMaxConcurrentStreamLimit] = useState(4);
  const [mockSmsProviderEnabled, setMockSmsProviderEnabled] = useState(true);
  const [mockPaymentProviderEnabled, setMockPaymentProviderEnabled] = useState(true);
  const [successMsg, setSuccessMsg] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const handleSaveSettings = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setTimeout(() => {
      setIsSaving(false);
      setSuccessMsg('System configuration and gateway security settings saved successfully!');
      setTimeout(() => setSuccessMsg(''), 3000);
    }, 600);
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="border-b border-slate-800 pb-4">
        <h3 className="text-lg font-bold text-white flex items-center gap-2">
          <Sliders className="w-5 h-5 text-red-500" />
          System Settings & Platform Configuration
        </h3>
        <p className="text-xs text-slate-400">Configure global API gateway security, JWT token expiry, HLS transcoding parameters, and mock provider modes.</p>
      </div>

      {successMsg && (
        <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 p-4 rounded-xl text-xs font-semibold flex items-center gap-2">
          <CheckCircle className="w-4 h-4" />
          {successMsg}
        </div>
      )}

      <form onSubmit={handleSaveSettings} className="space-y-6">
        {/* Security & Token Settings */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <Shield className="w-4 h-4 text-red-500" />
            Security & Authentication Settings
          </h4>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">API Gateway JWT Secret Key</label>
            <input
              type="text"
              required
              value={jwtSecret}
              onChange={(e) => setJwtSecret(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs font-mono text-slate-300 focus:outline-none focus:border-red-500"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Access Token Expiration (ms)</label>
              <input
                type="number"
                required
                value={accessTokenExpirationMs}
                onChange={(e) => setAccessTokenExpirationMs(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
              <span className="text-[10px] text-slate-500">Current: {accessTokenExpirationMs / 60000} minutes</span>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Refresh Token Expiration (ms)</label>
              <input
                type="number"
                required
                value={refreshTokenExpirationMs}
                onChange={(e) => setRefreshTokenExpirationMs(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
              <span className="text-[10px] text-slate-500">Current: {refreshTokenExpirationMs / 86400000} days</span>
            </div>
          </div>
        </div>

        {/* Streaming & Media Infrastructure Settings */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <HardDrive className="w-4 h-4 text-indigo-400" />
            HLS Media & Playback Pipeline
          </h4>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">HLS Segment Chunk Duration (Seconds)</label>
              <input
                type="number"
                required
                value={hlsSegmentDurationSeconds}
                onChange={(e) => setHlsSegmentDurationSeconds(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Global Max Concurrent Streams Enforcement</label>
              <input
                type="number"
                required
                value={maxConcurrentStreamLimit}
                onChange={(e) => setMaxConcurrentStreamLimit(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
            </div>
          </div>
        </div>

        {/* Integration Mock Providers */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <Database className="w-4 h-4 text-emerald-400" />
            Integrations & Service Providers
          </h4>

          <div className="space-y-3">
            <label className="flex items-center justify-between p-3 bg-slate-950 border border-slate-800 rounded-lg cursor-pointer">
              <div>
                <span className="text-xs font-semibold text-white block">Mock SMS Provider Mode (6-Digit OTP)</span>
                <span className="text-[11px] text-slate-400">Uses MockSmsProvider logging instead of Twilio/AWS SNS.</span>
              </div>
              <input
                type="checkbox"
                checked={mockSmsProviderEnabled}
                onChange={(e) => setMockSmsProviderEnabled(e.target.checked)}
                className="rounded border-slate-800 text-red-600 focus:ring-red-500 w-4 h-4"
              />
            </label>

            <label className="flex items-center justify-between p-3 bg-slate-950 border border-slate-800 rounded-lg cursor-pointer">
              <div>
                <span className="text-xs font-semibold text-white block">Mock Payment Gateway Mode</span>
                <span className="text-[11px] text-slate-400">Uses MockPaymentProvider for instant Stripe/PayPal transactions.</span>
              </div>
              <input
                type="checkbox"
                checked={mockPaymentProviderEnabled}
                onChange={(e) => setMockPaymentProviderEnabled(e.target.checked)}
                className="rounded border-slate-800 text-red-600 focus:ring-red-500 w-4 h-4"
              />
            </label>
          </div>
        </div>

        <div className="flex items-center justify-end gap-3">
          <button
            type="submit"
            disabled={isSaving}
            className="px-6 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-xl font-bold text-xs shadow-md transition flex items-center gap-2"
          >
            {isSaving ? <RefreshCw className="w-4 h-4 animate-spin" /> : null}
            {isSaving ? 'Saving System Configuration...' : 'Save Configuration Changes'}
          </button>
        </div>
      </form>
    </div>
  );
};
