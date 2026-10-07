import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { SubscriptionPlan } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { CreditCard, Plus, ArrowLeft, Check, Tv, Smartphone, Monitor } from 'lucide-react';

export const SubscriptionsPage: React.FC = () => {
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [isCreating, setIsCreating] = useState(false);
  const [name, setName] = useState('Premium');
  const [monthlyPrice, setMonthlyPrice] = useState(19.99);
  const [maxConcurrentStreams, setMaxConcurrentStreams] = useState(4);
  const [maxRegisteredDevices, setMaxRegisteredDevices] = useState(5);
  const [maxResolution, setMaxResolution] = useState('4K');
  const [offlineDownloadsAllowed, setOfflineDownloadsAllowed] = useState(true);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadPlans();
  }, []);

  const loadPlans = async () => {
    try {
      setLoading(true);
      const res = await adminService.getSubscriptionPlans();
      if (res.data) setPlans(res.data);
    } catch (err) {
      console.error('Failed to load plans:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCreatePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await adminService.createSubscriptionPlan({
        name,
        monthlyPrice,
        maxConcurrentStreams,
        maxRegisteredDevices,
        maxResolution,
        offlineDownloadsAllowed,
        version: 2,
        active: true,
      });
      if (res.data) {
        setPlans([...plans, res.data]);
        setIsCreating(false);
      }
    } catch (err) {
      console.error('Failed to create plan:', err);
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
            <h3 className="text-lg font-bold text-white">Create New Subscription Plan Version</h3>
            <p className="text-xs text-slate-400">Configure data-driven subscription terms and decoupled entitlement rules.</p>
          </div>
        </div>

        <form onSubmit={handleCreatePlan} className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Plan Tier Name</label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              placeholder="e.g. Ultra 4K Premium"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Monthly Price ($ USD)</label>
              <input
                type="number"
                step="0.01"
                required
                value={monthlyPrice}
                onChange={(e) => setMonthlyPrice(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Max Resolution Entitlement</label>
              <select
                value={maxResolution}
                onChange={(e) => setMaxResolution(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              >
                <option value="720p">720p HD</option>
                <option value="1080p">1080p Full HD</option>
                <option value="4K">4K Ultra HD + HDR</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Max Concurrent Streams</label>
              <input
                type="number"
                required
                value={maxConcurrentStreams}
                onChange={(e) => setMaxConcurrentStreams(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Max Registered Devices</label>
              <input
                type="number"
                required
                value={maxRegisteredDevices}
                onChange={(e) => setMaxRegisteredDevices(Number(e.target.value))}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-white focus:outline-none focus:border-red-500"
              />
            </div>
          </div>

          <div>
            <label className="flex items-center gap-2 text-xs text-slate-300 cursor-pointer pt-2">
              <input
                type="checkbox"
                checked={offlineDownloadsAllowed}
                onChange={(e) => setOfflineDownloadsAllowed(e.target.checked)}
                className="rounded border-slate-800 text-red-600 focus:ring-red-500"
              />
              Allow Offline Video Downloads on Mobile
            </label>
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
              Create Plan Version
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
          <h3 className="text-lg font-bold text-white">Subscription Plans & Entitlement Rules</h3>
          <p className="text-xs text-slate-400">Decoupled subscriber entitlement configurations and immutable plan versioning.</p>
        </div>
        <button
          onClick={() => setIsCreating(true)}
          className="flex items-center gap-2 px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition"
        >
          <Plus className="w-4 h-4" />
          Create Plan Version Page
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {plans.map((plan) => (
          <div key={plan.id} className="bg-slate-900 border border-slate-800 rounded-xl p-6 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Version {plan.version || 1}</span>
                <StatusBadge status={plan.active ? 'ACTIVE' : 'ARCHIVED'} />
              </div>
              <h4 className="text-xl font-bold text-white mb-1">{plan.name}</h4>
              <div className="flex items-baseline gap-1 mb-4">
                <span className="text-2xl font-extrabold text-white">${plan.monthlyPrice}</span>
                <span className="text-xs text-slate-400">/ month</span>
              </div>

              <div className="space-y-2.5 text-xs border-t border-slate-800 pt-4 text-slate-300">
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Max Resolution:</span>
                  <span className="font-semibold text-white">{plan.maxResolution}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Concurrent Streams:</span>
                  <span className="font-semibold text-white">{plan.maxConcurrentStreams} screens</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Registered Devices:</span>
                  <span className="font-semibold text-white">{plan.maxRegisteredDevices} devices</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Offline Downloads:</span>
                  <span className="font-semibold text-emerald-400">{plan.offlineDownloadsAllowed ? 'Enabled' : 'Disabled'}</span>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
