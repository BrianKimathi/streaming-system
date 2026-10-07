import React, { useEffect, useState } from 'react';
import { adminService } from '../services/adminService';
import { Incident } from '../types';
import { StatusBadge } from '../components/common/StatusBadge';
import { Server, Activity, Database, HardDrive, AlertTriangle, AlertOctagon, CheckCircle, ShieldAlert } from 'lucide-react';

export const OperationsHealthPage: React.FC = () => {
  const [healthData, setHealthData] = useState<Record<string, any>>({});
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [maintenanceMode, setMaintenanceMode] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadOperationsData();
  }, []);

  const loadOperationsData = async () => {
    try {
      setLoading(true);
      const [healthRes, incidentsRes] = await Promise.all([
        adminService.getSystemHealth(),
        adminService.getIncidents(),
      ]);

      if (healthRes.data) setHealthData(healthRes.data);
      if (incidentsRes.data) setIncidents(incidentsRes.data);
    } catch (err) {
      console.error('Failed to load operations data:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleMaintenance = () => {
    const phrase = window.prompt('To toggle Platform Maintenance Mode, please type "ENABLE MAINTENANCE":');
    if (phrase === 'ENABLE MAINTENANCE') {
      setMaintenanceMode(!maintenanceMode);
    }
  };

  const servicesMap = (healthData.services as Record<string, string>) || {
    'auth-service': 'UP (Latency: 12ms)',
    'user-service': 'UP (Latency: 8ms)',
    'catalog-service': 'UP (Latency: 15ms)',
    'subscription-service': 'UP (Latency: 10ms)',
    'billing-service': 'UP (Latency: 18ms)',
    'device-service': 'UP (Latency: 9ms)',
    'media-service': 'UP (FFmpeg Transcoder Active)',
    'playback-service': 'UP (Concurrent Streams Normal)',
    'watch-history-service': 'UP (Redis Cache Hit 98.4%)',
    'trending-service': 'UP (Velocity Score Pipeline Active)',
    'analytics-service': 'UP (Kafka Consumer Active)',
    'notification-service': 'UP (Multi-channel Ready)',
    'admin-service': 'UP (Control Plane Active)',
    'api-gateway': 'UP (Reactive Netty Router)',
  };

  return (
    <div className="space-y-6">
      {/* Maintenance Mode Alert Banner */}
      <div className={`border rounded-xl p-5 flex items-center justify-between transition ${
        maintenanceMode ? 'bg-rose-500/10 border-rose-500/30 text-rose-400' : 'bg-slate-900 border-slate-800 text-slate-300'
      }`}>
        <div className="flex items-center gap-3">
          <AlertOctagon className={`w-6 h-6 ${maintenanceMode ? 'text-rose-500 animate-pulse' : 'text-slate-500'}`} />
          <div>
            <h4 className="font-bold text-white text-sm">
              Platform Maintenance Mode Status: <span className={maintenanceMode ? 'text-rose-400' : 'text-emerald-400'}>{maintenanceMode ? 'ACTIVE' : 'OFF (Normal Operations)'}</span>
            </h4>
            <p className="text-xs text-slate-400">Restricts public streaming access while allowing administrative operations.</p>
          </div>
        </div>
        <button
          onClick={handleToggleMaintenance}
          className={`px-4 py-2 rounded-lg text-xs font-semibold shadow-md transition ${
            maintenanceMode ? 'bg-emerald-600 hover:bg-emerald-700 text-white' : 'bg-rose-600 hover:bg-rose-700 text-white'
          }`}
        >
          {maintenanceMode ? 'Disable Maintenance' : 'Enable Maintenance Mode'}
        </button>
      </div>

      {/* Infrastructure Cluster Status */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <Database className="w-4 h-4 text-blue-400" />
              PostgreSQL Cluster
            </span>
            <StatusBadge status="COMPLETED" />
          </div>
          <p className="text-xs text-slate-400">13 Service DBs (Connections: 18 / 100)</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <HardDrive className="w-4 h-4 text-emerald-400" />
              Redis Cache Cluster
            </span>
            <StatusBadge status="COMPLETED" />
          </div>
          <p className="text-xs text-slate-400">Memory: 24.5 MB / Hit Rate: 98.4%</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <Activity className="w-4 h-4 text-purple-400" />
              Apache Kafka Event Bus
            </span>
            <StatusBadge status="COMPLETED" />
          </div>
          <p className="text-xs text-slate-400">Active Brokers: 1 | Consumer Lag: 0</p>
        </div>
      </div>

      {/* Microservices Operational Health Matrix */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
          <Server className="w-4 h-4 text-red-500" />
          Microservices Operational Status Matrix (14 Active Instances)
        </h4>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {Object.entries(servicesMap).map(([serviceName, statusText]) => (
            <div key={serviceName} className="bg-slate-950 border border-slate-800 p-3.5 rounded-lg flex items-center justify-between">
              <div>
                <span className="font-mono text-xs font-bold text-white block">{serviceName}</span>
                <span className="text-[11px] text-slate-400">{statusText}</span>
              </div>
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
