import React from 'react';

interface StatusBadgeProps {
  status: string;
}

const POSITIVE = ['PUBLISHED', 'ACTIVE', 'SUCCESS', 'COMPLETED', 'SENT', 'DELIVERED', 'UP', 'RESOLVED', 'READY', 'TRIAL'];
const PENDING = ['DRAFT', 'PENDING', 'PROCESSING', 'UPLOADING', 'INITIATED', 'SCHEDULED', 'OPEN', 'IN_PROGRESS', 'INVESTIGATING', 'MITIGATED', 'PAST_DUE', 'GRACE_PERIOD', 'EXPIRING', 'DEGRADED'];
const NEGATIVE = ['REVOKED', 'FAILED', 'ARCHIVED', 'REFUNDED', 'DOWN', 'SUSPENDED', 'CANCELLED', 'EXPIRED', 'BLOCKED', 'DELETED'];

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const value = (status ?? 'UNKNOWN').toUpperCase();
  let colors = 'bg-slate-800 text-slate-300 border-slate-700';
  if (POSITIVE.includes(value)) colors = 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
  else if (PENDING.includes(value)) colors = 'bg-amber-500/10 text-amber-400 border-amber-500/20';
  else if (NEGATIVE.includes(value)) colors = 'bg-rose-500/10 text-rose-400 border-rose-500/20';

  return (
    <span className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border inline-flex items-center gap-1.5 whitespace-nowrap ${colors}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current"></span>
      {value.replace(/_/g, ' ')}
    </span>
  );
};
