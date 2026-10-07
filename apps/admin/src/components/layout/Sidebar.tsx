import React from 'react';
import {
  LayoutDashboard,
  Users,
  Film,
  UploadCloud,
  CreditCard,
  DollarSign,
  Smartphone,
  TrendingUp,
  Bell,
  LogOut,
  Tv,
  Tag,
  Sliders,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

interface SidebarProps {
  currentTab: string;
  onTabChange: (tab: string) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentTab, onTabChange }) => {
  const { logout, email } = useAuth();

  const navItems = [
    { id: 'dashboard', label: 'Executive Dashboard', icon: LayoutDashboard },
    { id: 'users', label: 'User Accounts & Blocking', icon: Users },
    { id: 'catalog', label: 'Movie & TV Catalog', icon: Film },
    { id: 'genres', label: 'Genre Categories', icon: Tag },
    { id: 'media', label: 'HLS Media Pipeline', icon: UploadCloud },
    { id: 'subscriptions', label: 'Subscription Plans', icon: CreditCard },
    { id: 'billing', label: 'Billing & Refunds', icon: DollarSign },
    { id: 'devices', label: 'Device Control', icon: Smartphone },
    { id: 'trending', label: 'Velocity Intelligence', icon: TrendingUp },
    { id: 'notifications', label: 'Notifications Log', icon: Bell },
    { id: 'settings', label: 'System Settings', icon: Sliders },
  ];

  return (
    <aside className="w-64 bg-slate-900 border-r border-slate-800 flex flex-col h-screen sticky top-0">
      {/* Brand Header */}
      <div className="h-16 flex items-center gap-3 px-6 border-b border-slate-800">
        <div className="w-9 h-9 rounded-lg bg-red-600 flex items-center justify-center">
          <Tv className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="font-extrabold text-lg tracking-wider text-white">STREAM<span className="text-red-500">X</span></h1>
          <p className="text-[10px] text-slate-400 uppercase tracking-widest font-semibold">Admin Panel</p>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentTab === item.id || currentTab.startsWith(`${item.id}-`);
          return (
            <button
              key={item.id}
              onClick={() => onTabChange(item.id)}
              className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg font-medium text-xs transition-all ${
                isActive
                  ? 'bg-red-600 text-white font-semibold'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Icon className="w-4 h-4" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* User Footer */}
      <div className="p-4 border-t border-slate-800 bg-slate-900">
        <div className="flex items-center justify-between">
          <div className="truncate pr-2">
            <p className="text-xs font-semibold text-white truncate">{email || 'admin@streamx.io'}</p>
            <p className="text-[11px] text-slate-400">Super Administrator</p>
          </div>
          <button
            onClick={logout}
            title="Log Out"
            className="p-2 rounded-lg text-slate-400 hover:text-red-400 hover:bg-slate-800 transition"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </aside>
  );
};
