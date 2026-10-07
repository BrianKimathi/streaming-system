import React from 'react';
import { AlertCircle, CheckCircle2, Inbox, Loader2 } from 'lucide-react';

export const ErrorBanner: React.FC<{ message: string | null; onRetry?: () => void }> = ({ message, onRetry }) => {
  if (!message) return null;
  return (
    <div className="bg-rose-500/10 border border-rose-500/20 text-rose-300 p-3 rounded-lg text-xs font-medium flex items-center justify-between gap-3">
      <span className="flex items-center gap-2">
        <AlertCircle className="w-4 h-4 shrink-0" />
        {message}
      </span>
      {onRetry && (
        <button onClick={onRetry} className="px-2.5 py-1 rounded-md border border-rose-500/30 hover:bg-rose-500/10 font-semibold">
          Retry
        </button>
      )}
    </div>
  );
};

export const SuccessBanner: React.FC<{ message: string | null }> = ({ message }) => {
  if (!message) return null;
  return (
    <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 p-3 rounded-lg text-xs font-medium flex items-center gap-2">
      <CheckCircle2 className="w-4 h-4 shrink-0" />
      {message}
    </div>
  );
};

export const LoadingState: React.FC<{ label?: string }> = ({ label = 'Loading…' }) => (
  <div className="flex items-center justify-center gap-2 py-10 text-xs text-slate-400">
    <Loader2 className="w-4 h-4 animate-spin text-red-500" />
    {label}
  </div>
);

export const EmptyState: React.FC<{ title: string; hint?: string }> = ({ title, hint }) => (
  <div className="flex flex-col items-center justify-center gap-2 py-10 text-center">
    <Inbox className="w-6 h-6 text-slate-600" />
    <p className="text-xs font-semibold text-slate-300">{title}</p>
    {hint && <p className="text-[11px] text-slate-500 max-w-sm">{hint}</p>}
  </div>
);
