import React from 'react';

interface StatusBadgeProps {
  status: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const getColors = (val: string) => {
    switch (val.toUpperCase()) {
      case 'PUBLISHED':
      case 'ACTIVE':
      case 'SUCCESS':
      case 'COMPLETED':
      case 'SENT':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
      case 'DRAFT':
      case 'PENDING':
      case 'PROCESSING':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
      case 'REVOKED':
      case 'FAILED':
      case 'ARCHIVED':
      case 'REFUNDED':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/20';
      default:
        return 'bg-slate-800 text-slate-300 border-slate-700';
    }
  };

  return (
    <span
      className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border inline-flex items-center gap-1.5 ${getColors(
        status
      )}`}
    >
      <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
      {status}
    </span>
  );
};
