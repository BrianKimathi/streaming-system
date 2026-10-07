import React from 'react';
import { LucideIcon } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: LucideIcon;
  iconBgColor?: string;
  unavailable?: boolean;
}

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  icon: Icon,
  iconBgColor = 'bg-red-500/10 text-red-500',
  unavailable = false,
}) => {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-sm hover:border-slate-700 transition-all">
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm font-medium text-slate-400">{title}</span>
        <div className={`p-2.5 rounded-lg ${iconBgColor}`}>
          <Icon className="w-5 h-5" />
        </div>
      </div>
      <h3 className={`text-2xl font-bold tracking-tight ${unavailable ? 'text-slate-600' : 'text-white'}`}>
        {unavailable ? 'Unavailable' : value}
      </h3>
      {subtitle && <p className="mt-1 text-[11px] text-slate-500">{subtitle}</p>}
    </div>
  );
};
