import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Sidebar } from './components/layout/Sidebar';
import { Header } from './components/layout/Header';
import { DashboardPage } from './pages/DashboardPage';
import { UsersPage } from './pages/UsersPage';
import { CatalogPage } from './pages/CatalogPage';
import { GenresPage } from './pages/GenresPage';
import { MediaPipelinePage } from './pages/MediaPipelinePage';
import { SubscriptionsPage } from './pages/SubscriptionsPage';
import { BillingPage } from './pages/BillingPage';
import { DevicesPage } from './pages/DevicesPage';
import { TrendingPage } from './pages/TrendingPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { SupportCenterPage } from './pages/SupportCenterPage';
import { AuditLogsPage } from './pages/AuditLogsPage';
import { FeatureFlagsPage } from './pages/FeatureFlagsPage';
import { OperationsHealthPage } from './pages/OperationsHealthPage';
import { SettingsPage } from './pages/SettingsPage';
import { LoginPage } from './pages/LoginPage';

const MainLayout: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const [currentTab, setCurrentTab] = useState('dashboard');

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  const getTabTitle = (tab: string) => {
    switch (tab) {
      case 'dashboard':
        return 'Platform Overview';
      case 'users':
        return 'User Accounts';
      case 'catalog':
        return 'Movie & TV Catalog';
      case 'genres':
        return 'Genres';
      case 'media':
        return 'Media Uploads & HLS Transcoding';
      case 'subscriptions':
        return 'Subscriptions & Plans';
      case 'billing':
        return 'Billing Transactions & Refunds';
      case 'devices':
        return 'Registered Devices';
      case 'trending':
        return 'Trending Content';
      case 'notifications':
        return 'Notification Delivery Log';
      case 'support':
        return 'Support Tickets';
      case 'audit-logs':
        return 'Admin Audit Trail';
      case 'flags':
        return 'Feature Flags';
      case 'ops-health':
        return 'Operations & Service Health';
      case 'settings':
        return 'Settings';
      default:
        return 'Control Center';
    }
  };

  const renderContent = () => {
    switch (currentTab) {
      case 'dashboard':
        return <DashboardPage />;
      case 'users':
        return <UsersPage />;
      case 'catalog':
        return <CatalogPage />;
      case 'genres':
        return <GenresPage />;
      case 'media':
        return <MediaPipelinePage />;
      case 'subscriptions':
        return <SubscriptionsPage />;
      case 'billing':
        return <BillingPage />;
      case 'devices':
        return <DevicesPage />;
      case 'trending':
        return <TrendingPage />;
      case 'notifications':
        return <NotificationsPage />;
      case 'support':
        return <SupportCenterPage />;
      case 'audit-logs':
        return <AuditLogsPage />;
      case 'flags':
        return <FeatureFlagsPage />;
      case 'ops-health':
        return <OperationsHealthPage />;
      case 'settings':
        return <SettingsPage />;
      default:
        return <DashboardPage />;
    }
  };

  return (
    <div className="flex min-h-screen bg-slate-950 text-slate-100 font-sans">
      <Sidebar currentTab={currentTab} onTabChange={setCurrentTab} />
      <div className="flex-1 flex flex-col min-w-0">
        <Header title={getTabTitle(currentTab)} />
        <main className="flex-1 p-8 overflow-y-auto">{renderContent()}</main>
      </div>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <MainLayout />
    </AuthProvider>
  );
};

export default App;
