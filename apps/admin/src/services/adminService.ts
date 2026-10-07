import api from '../api/client';
import {
  ApiResponse,
  Movie,
  TVShow,
  Genre,
  SubscriptionPlan,
  PaymentTransaction,
  DeviceRegistration,
  MediaAsset,
  TrendingItem,
  AnalyticsDashboard,
  NotificationLog,
  UserAccount,
  AuditLog,
  FeatureFlag,
  Incident,
  SupportTicket,
  PromotionCoupon,
} from '../types';

export const adminService = {
  // --- Auth & Account Management ---
  login: async (email: string, password: string) => {
    const res = await api.post<ApiResponse<{ token: string; accountId: string }>>('/auth/login', {
      email,
      password,
    });
    return res.data;
  },

  getAccounts: async () => {
    const res = await api.get<ApiResponse<UserAccount[]>>('/auth/accounts');
    return res.data;
  },

  toggleAccountBlock: async (accountId: string, isBlocked: boolean) => {
    const res = await api.patch<ApiResponse<UserAccount>>(`/auth/accounts/${accountId}/status`, {
      blocked: isBlocked,
    });
    return res.data;
  },

  // --- Audit Logs ---
  getAuditLogs: async () => {
    const res = await api.get<ApiResponse<AuditLog[]>>('/admin/audit-logs');
    return res.data;
  },

  recordAuditLog: async (logEntry: Partial<AuditLog>) => {
    const res = await api.post<ApiResponse<AuditLog>>('/admin/audit-logs', logEntry);
    return res.data;
  },

  // --- Feature Flags ---
  getFeatureFlags: async () => {
    const res = await api.get<ApiResponse<FeatureFlag[]>>('/admin/feature-flags');
    return res.data;
  },

  toggleFeatureFlag: async (flagKey: string, enabled: boolean, targetPercentage: number, reason: string) => {
    const res = await api.post<ApiResponse<FeatureFlag>>('/admin/feature-flags/toggle', null, {
      params: { flagKey, enabled, targetPercentage, reason },
    });
    return res.data;
  },

  // --- Incidents ---
  getIncidents: async () => {
    const res = await api.get<ApiResponse<Incident[]>>('/admin/incidents');
    return res.data;
  },

  createIncident: async (title: string, description: string, severity: string, affectedServices: string) => {
    const res = await api.post<ApiResponse<Incident>>('/admin/incidents', null, {
      params: { title, description, severity, affectedServices },
    });
    return res.data;
  },

  // --- Support Tickets ---
  getSupportTickets: async () => {
    const res = await api.get<ApiResponse<SupportTicket[]>>('/admin/tickets');
    return res.data;
  },

  createSupportTicket: async (ticket: Partial<SupportTicket>) => {
    const res = await api.post<ApiResponse<SupportTicket>>('/admin/tickets', null, {
      params: {
        accountId: ticket.accountId,
        userEmail: ticket.userEmail,
        category: ticket.category,
        priority: ticket.priority,
        subject: ticket.subject,
        body: ticket.body,
      },
    });
    return res.data;
  },

  // --- System Health ---
  getSystemHealth: async () => {
    const res = await api.get<ApiResponse<Record<string, any>>>('/admin/system/health');
    return res.data;
  },

  // --- Analytics ---
  getDashboardAnalytics: async (date?: string) => {
    const params = date ? { date } : {};
    const res = await api.get<ApiResponse<AnalyticsDashboard>>('/analytics/dashboard', { params });
    return res.data;
  },

  recordAnalyticsEvent: async (event: {
    watchTimeSeconds: number;
    completionPercentage: number;
    newActiveUser: boolean;
    subscriptionPaymentAmount: number;
  }) => {
    const res = await api.post<ApiResponse<AnalyticsDashboard>>('/analytics/record', event);
    return res.data;
  },

  // --- Catalog ---
  getMovies: async () => {
    const res = await api.get<ApiResponse<Movie[]>>('/catalog/movies');
    return res.data;
  },

  createMovie: async (movieData: Partial<Movie>) => {
    const res = await api.post<ApiResponse<Movie>>('/catalog/movies', movieData);
    return res.data;
  },

  updateMovieStatus: async (id: string, status: string) => {
    const res = await api.patch<ApiResponse<Movie>>(`/catalog/movies/${id}/status`, { status });
    return res.data;
  },

  getTVShows: async () => {
    const res = await api.get<ApiResponse<TVShow[]>>('/catalog/tv-shows');
    return res.data;
  },

  createTVShow: async (showData: Partial<TVShow>) => {
    const res = await api.post<ApiResponse<TVShow>>('/catalog/tv-shows', showData);
    return res.data;
  },

  getGenres: async () => {
    const res = await api.get<ApiResponse<Genre[]>>('/catalog/genres');
    return res.data;
  },

  createGenre: async (name: string, description?: string) => {
    const res = await api.post<ApiResponse<Genre>>('/catalog/genres', { name, description });
    return res.data;
  },

  // --- Media & Transcoding ---
  getMediaAssets: async () => {
    const res = await api.get<ApiResponse<MediaAsset[]>>('/media/assets');
    return res.data;
  },

  uploadMedia: async (contentId: string, filename: string) => {
    const res = await api.post<ApiResponse<MediaAsset>>('/media/upload', { contentId, filename });
    return res.data;
  },

  // --- Subscriptions & Entitlements ---
  getSubscriptionPlans: async () => {
    const res = await api.get<ApiResponse<SubscriptionPlan[]>>('/subscriptions/plans');
    return res.data;
  },

  createSubscriptionPlan: async (plan: Partial<SubscriptionPlan>) => {
    const res = await api.post<ApiResponse<SubscriptionPlan>>('/subscriptions/plans', plan);
    return res.data;
  },

  // --- Billing & Refunds ---
  getBillingHistory: async () => {
    const res = await api.get<ApiResponse<PaymentTransaction[]>>('/billing/history');
    return res.data;
  },

  processRefund: async (transactionId: string) => {
    const res = await api.post<ApiResponse<PaymentTransaction>>(`/billing/refund/${transactionId}`);
    return res.data;
  },

  // --- Device Management ---
  getRegisteredDevices: async () => {
    const res = await api.get<ApiResponse<DeviceRegistration[]>>('/devices');
    return res.data;
  },

  revokeDevice: async (deviceId: string) => {
    const res = await api.post<ApiResponse<DeviceRegistration>>(`/devices/${deviceId}/revoke`);
    return res.data;
  },

  // --- Trending Intelligence ---
  getTopTrending: async (limit = 20) => {
    const res = await api.get<ApiResponse<TrendingItem[]>>('/trending', { params: { limit } });
    return res.data;
  },

  recordTrendingEvent: async (event: {
    contentId: string;
    title: string;
    contentType: string;
    eventType: 'VIEW' | 'COMPLETION' | 'LIKE';
  }) => {
    const res = await api.post<ApiResponse<TrendingItem>>('/trending/record', event);
    return res.data;
  },

  // --- Notifications ---
  getUserNotifications: async (accountId?: string) => {
    const params = accountId ? { accountId } : {};
    const res = await api.get<ApiResponse<NotificationLog[]>>('/notifications/user', { params });
    return res.data;
  },

  sendNotification: async (notification: {
    accountId: string;
    recipient: string;
    channel: 'EMAIL' | 'SMS';
    template: string;
    subject: string;
    body: string;
  }) => {
    const res = await api.post<ApiResponse<NotificationLog>>('/notifications/send', notification);
    return res.data;
  },
};
