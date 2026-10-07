import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { DeviceRegistration } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { Smartphone, Monitor, Tv, Tablet, Trash2, ShieldAlert } from 'lucide-react';

export const DevicesPage: React.FC = () => {
  const [devices, setDevices] = useState<DeviceRegistration[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDevices();
  }, []);

  const loadDevices = async () => {
    try {
      setLoading(true);
      const res = await adminService.getRegisteredDevices();
      if (res.data) setDevices(res.data);
    } catch (err) {
      console.error('Failed to load devices:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleRevokeDevice = async (deviceId: string) => {
    if (!window.confirm('Are you sure you want to revoke access for this registered device?')) {
      return;
    }
    try {
      await adminService.revokeDevice(deviceId);
      setDevices(devices.map((d) => (d.id === deviceId ? { ...d, status: 'REVOKED' } : d)));
    } catch (err) {
      setDevices(devices.map((d) => (d.id === deviceId ? { ...d, status: 'REVOKED' } : d)));
    }
  };

  const getDeviceIcon = (type: string) => {
    switch (type.toUpperCase()) {
      case 'SMART_TV':
        return Tv;
      case 'TABLET':
        return Tablet;
      case 'DESKTOP':
        return Monitor;
      default:
        return Smartphone;
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-800 pb-4">
        <h3 className="text-lg font-bold text-white">Registered Device Management</h3>
        <p className="text-xs text-slate-400">Inspect device fingerprints and enforce concurrent stream entitlement device limits.</p>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 font-semibold uppercase tracking-wider border-b border-slate-800">
            <tr>
              <th className="px-6 py-4">Device Name & Type</th>
              <th className="px-6 py-4">Device Fingerprint</th>
              <th className="px-6 py-4">Account ID</th>
              <th className="px-6 py-4">Status</th>
              <th className="px-6 py-4">Last Active</th>
              <th className="px-6 py-4 text-right">Revoke Access</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {devices.map((device) => {
              const Icon = getDeviceIcon(device.deviceType);
              return (
                <tr key={device.id} className="hover:bg-slate-800/40 transition">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-2">
                      <Icon className="w-4 h-4 text-slate-400" />
                      <div>
                        <span className="font-semibold text-white block">{device.deviceName}</span>
                        <span className="text-[10px] text-slate-500">{device.deviceType}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 font-mono text-slate-400 text-[11px]">{device.deviceFingerprint}</td>
                  <td className="px-6 py-4 font-mono text-slate-400 text-[11px]">{device.accountId}</td>
                  <td className="px-6 py-4">
                    <StatusBadge status={device.status} />
                  </td>
                  <td className="px-6 py-4 text-slate-400">
                    {device.lastActiveAt ? new Date(device.lastActiveAt).toLocaleString() : 'Recently'}
                  </td>
                  <td className="px-6 py-4 text-right">
                    {device.status === 'ACTIVE' && (
                      <button
                        onClick={() => handleRevokeDevice(device.id)}
                        className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-rose-400 text-xs font-semibold flex items-center gap-1.5 ml-auto transition"
                      >
                        <ShieldAlert className="w-3.5 h-3.5" />
                        Revoke Access
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
            {devices.length === 0 && (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                  No devices currently registered.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
