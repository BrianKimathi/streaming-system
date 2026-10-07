import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { FeatureFlag } from '../types';
import { ToggleLeft, ToggleRight, Sliders, CheckCircle, Percent } from 'lucide-react';

export const FeatureFlagsPage: React.FC = () => {
  const [flags, setFlags] = useState<FeatureFlag[]>([]);
  const [loading, setLoading] = useState(true);
  const [successMsg, setSuccessMsg] = useState('');

  useEffect(() => {
    loadFlags();
  }, []);

  const loadFlags = async () => {
    try {
      setLoading(true);
      const res = await adminService.getFeatureFlags();
      if (res.data) setFlags(res.data);
    } catch (err) {
      console.error('Failed to load feature flags:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleFlag = async (flag: FeatureFlag) => {
    const nextState = !flag.enabled;
    const reason = window.prompt(`Please provide an administrative reason for changing feature flag ${flag.flagKey}:`);
    if (!reason || reason.trim() === '') return;

    try {
      await adminService.toggleFeatureFlag(flag.flagKey, nextState, flag.targetPercentage, reason);
      setFlags(flags.map((f) => (f.flagKey === flag.flagKey ? { ...f, enabled: nextState } : f)));
      setSuccessMsg(`Feature flag ${flag.flagKey} updated to ${nextState ? 'ENABLED' : 'DISABLED'}`);
      setTimeout(() => setSuccessMsg(''), 2500);
    } catch (err) {
      setFlags(flags.map((f) => (f.flagKey === flag.flagKey ? { ...f, enabled: nextState } : f)));
    }
  };

  const handlePercentageChange = async (flag: FeatureFlag, newPercentage: number) => {
    try {
      await adminService.toggleFeatureFlag(flag.flagKey, flag.enabled, newPercentage, 'Target rollout percentage update');
      setFlags(flags.map((f) => (f.flagKey === flag.flagKey ? { ...f, targetPercentage: newPercentage } : f)));
    } catch (err) {
      setFlags(flags.map((f) => (f.flagKey === flag.flagKey ? { ...f, targetPercentage: newPercentage } : f)));
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-800 pb-4">
        <h3 className="text-lg font-bold text-white flex items-center gap-2">
          <Sliders className="w-5 h-5 text-red-500" />
          Feature Flags & Controlled Rollouts
        </h3>
        <p className="text-xs text-slate-400">Safely enable or disable platform capabilities and configure progressive percentage rollouts.</p>
      </div>

      {successMsg && (
        <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 p-4 rounded-xl text-xs font-semibold flex items-center gap-2">
          <CheckCircle className="w-4 h-4" />
          {successMsg}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {flags.map((flag) => (
          <div key={flag.flagKey} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-sm font-bold text-white font-mono">{flag.flagKey}</h4>
                <p className="text-xs text-slate-400">{flag.description}</p>
              </div>
              <button
                onClick={() => handleToggleFlag(flag)}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                  flag.enabled
                    ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                    : 'bg-slate-800 hover:bg-slate-700 text-slate-400'
                }`}
              >
                {flag.enabled ? <ToggleRight className="w-4 h-4" /> : <ToggleLeft className="w-4 h-4" />}
                {flag.enabled ? 'ENABLED' : 'DISABLED'}
              </button>
            </div>

            <div className="border-t border-slate-800 pt-4 space-y-2">
              <div className="flex items-center justify-between text-xs text-slate-300">
                <span className="flex items-center gap-1 text-slate-400">
                  <Percent className="w-3.5 h-3.5" />
                  Target Rollout Percentage:
                </span>
                <span className="font-bold text-white">{flag.targetPercentage}% of users</span>
              </div>
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                value={flag.targetPercentage}
                onChange={(e) => handlePercentageChange(flag, Number(e.target.value))}
                className="w-full h-1.5 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-red-600"
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
