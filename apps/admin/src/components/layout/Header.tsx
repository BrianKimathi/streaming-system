import React, { useCallback, useEffect, useState } from 'react';
import { ShieldCheck, ShieldAlert, Loader2 } from 'lucide-react';
import { adminService } from '../../services/adminService';
import type { SystemHealth } from '../../types';

interface HeaderProps {
  title: string;
}

type GatewayState = { kind: 'checking' } | { kind: 'ok'; health: SystemHealth } | { kind: 'error' };

export const Header: React.FC<HeaderProps> = ({ title }) => {
  const [state, setState] = useState<GatewayState>({ kind: 'checking' });

  const check = useCallback(async () => {
    try {
      const health = await adminService.getSystemHealth();
      setState({ kind: 'ok', health });
    } catch {
      setState({ kind: 'error' });
    }
  }, []);

  useEffect(() => {
    check();
    const interval = window.setInterval(check, 60_000);
    return () => window.clearInterval(interval);
  }, [check]);

  let badge: React.ReactNode;
  if (state.kind === 'checking') {
    badge = (
      <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-slate-800 border border-slate-700 text-slate-300 text-xs font-medium">
        <Loader2 className="w-3.5 h-3.5 animate-spin" />
        <span>Checking platform…</span>
      </div>
    );
  } else if (state.kind === 'error') {
    badge = (
      <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs font-medium">
        <ShieldAlert className="w-3.5 h-3.5" />
        <span>API unreachable</span>
      </div>
    );
  } else if (state.health.status === 'UP') {
    badge = (
      <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
        <ShieldCheck className="w-3.5 h-3.5" />
        <span>All systems operational</span>
      </div>
    );
  } else {
    badge = (
      <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs font-medium">
        <ShieldAlert className="w-3.5 h-3.5" />
        <span>{state.health.componentsDown} component(s) down</span>
      </div>
    );
  }

  return (
    <header className="h-16 border-b border-slate-800 bg-slate-950/80 backdrop-blur-md px-8 flex items-center justify-between sticky top-0 z-40">
      <h2 className="text-xl font-bold text-white tracking-tight">{title}</h2>
      {badge}
    </header>
  );
};
