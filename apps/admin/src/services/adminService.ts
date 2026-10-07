import axios from 'axios';
import api from '../api/client';
import type {
  AccountStats,
  ApiResponse,
  AuditEntry,
  AuditLog,
  AuthResponse,
  BillingStats,
  CatalogLookup,
  CatalogStats,
  ContentStatus,
  CreateMovieRequest,
  CreatePlanRequest,
  CreateTvShowRequest,
  CreateUploadRequest,
  DeviceRegistration,
  DeviceStats,
  Episode,
  EpisodeRequest,
  FeatureFlag,
  Genre,
  Incident,
  IncidentStatus,
  MediaAsset,
  MediaFile,
  MediaPreview,
  MediaStats,
  Movie,
  MpesaSettings,
  MpesaSettingsUpdate,
  MpesaTestResult,
  NotificationLog,
  NotificationStats,
  Page,
  PaymentTransaction,
  PlatformDashboard,
  Season,
  SeasonRequest,
  SendNotificationRequest,
  Subscription,
  SubscriptionPlan,
  SubscriptionStats,
  SupportTicket,
  SystemHealth,
  TrendingItem,
  TVShow,
  TvShowDetail,
  UpdateMovieRequest,
  UpdateTvShowRequest,
  UploadCompleteResponse,
  UploadPartResponse,
  UploadPurpose,
  UploadSession,
  UserAccount,
} from '../types';

async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await api.get<ApiResponse<T>>(url, { params });
  return res.data.data;
}

async function post<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  const res = await api.post<ApiResponse<T>>(url, body ?? null, { params });
  return res.data.data;
}

async function patch<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  const res = await api.patch<ApiResponse<T>>(url, body ?? null, { params });
  return res.data.data;
}

async function put<T>(url: string, body?: unknown): Promise<T> {
  const res = await api.put<ApiResponse<T>>(url, body ?? null);
  return res.data.data;
}

async function del(url: string): Promise<void> {
  await api.delete<ApiResponse<unknown>>(url);
}

export const adminService = {
  // --- Auth & accounts ---
  adminLogin: (usernameOrEmail: string, password: string) =>
    post<AuthResponse>('/auth/admin/login', { usernameOrEmail, password }),
  getAccounts: () => get<UserAccount[]>('/auth/admin/accounts'),
  getAccountStats: () => get<AccountStats>('/auth/admin/stats'),
  setAccountBlocked: (accountId: string, blocked: boolean) =>
    patch<UserAccount>(`/auth/admin/accounts/${accountId}/status`, { blocked }),

  // --- Platform overview ---
  getDashboard: () => get<PlatformDashboard>('/analytics/dashboard'),

  // --- Audit ---
  getAuditLogs: () => get<AuditLog[]>('/admin/audit-logs'),
  recordAudit: (entry: AuditEntry) => post<AuditLog>('/admin/audit-logs', entry),

  // --- Feature flags ---
  getFeatureFlags: () => get<FeatureFlag[]>('/admin/feature-flags'),
  createFeatureFlag: (flagKey: string, description: string, enabled: boolean, targetPercentage: number) =>
    post<FeatureFlag>('/admin/feature-flags', null, { flagKey, description, enabled, targetPercentage }),
  toggleFeatureFlag: (flagKey: string, enabled: boolean, targetPercentage: number, reason?: string) =>
    post<FeatureFlag>('/admin/feature-flags/toggle', null, { flagKey, enabled, targetPercentage, reason }),

  // --- Incidents ---
  getIncidents: () => get<Incident[]>('/admin/incidents'),
  createIncident: (title: string, description: string, severity: string, affectedServices: string) =>
    post<Incident>('/admin/incidents', null, { title, description, severity, affectedServices }),
  updateIncidentStatus: (id: string, status: IncidentStatus) =>
    patch<Incident>(`/admin/incidents/${id}/status`, null, { status }),

  // --- Support tickets ---
  getSupportTickets: () => get<SupportTicket[]>('/admin/tickets'),
  createSupportTicket: (ticket: {
    accountId?: string;
    userEmail?: string;
    category: string;
    priority: string;
    subject: string;
    body?: string;
  }) => post<SupportTicket>('/admin/tickets', null, ticket),
  updateSupportTicket: (id: string, changes: { status?: string; assignedAgentEmail?: string }) =>
    patch<SupportTicket>(`/admin/tickets/${id}`, null, changes),

  // --- System health ---
  getSystemHealth: () => get<SystemHealth>('/admin/system/health'),

  // --- Catalog ---
  getMovies: () => get<Movie[]>('/catalog/admin/movies'),
  createMovie: (movie: CreateMovieRequest) => post<Movie>('/catalog/movies', movie),
  updateMovie: (id: string, movie: UpdateMovieRequest) => put<Movie>(`/catalog/admin/movies/${id}`, movie),
  updateMovieStatus: (id: string, status: ContentStatus) =>
    patch<Movie>(`/catalog/admin/movies/${id}/status`, { status }),
  getTVShows: () => get<TVShow[]>('/catalog/admin/tv-shows'),
  createTVShow: (show: CreateTvShowRequest) => post<TVShow>('/catalog/admin/tv-shows', show),
  updateTVShow: (id: string, show: UpdateTvShowRequest) => put<TVShow>(`/catalog/admin/tv-shows/${id}`, show),
  updateTVShowStatus: (id: string, status: ContentStatus) =>
    patch<TVShow>(`/catalog/admin/tv-shows/${id}/status`, { status }),
  getTVShowDetail: (showId: string) => get<TvShowDetail>(`/catalog/admin/tv-shows/${showId}`),
  createSeason: (showId: string, season: SeasonRequest) =>
    post<Season>(`/catalog/admin/tv-shows/${showId}/seasons`, season),
  updateSeason: (seasonId: string, season: SeasonRequest) => put<Season>(`/catalog/admin/seasons/${seasonId}`, season),
  deleteSeason: (seasonId: string) => del(`/catalog/admin/seasons/${seasonId}`),
  createEpisode: (seasonId: string, episode: EpisodeRequest) =>
    post<Episode>(`/catalog/admin/seasons/${seasonId}/episodes`, episode),
  updateEpisode: (episodeId: string, episode: EpisodeRequest) =>
    put<Episode>(`/catalog/admin/episodes/${episodeId}`, episode),
  deleteEpisode: (episodeId: string) => del(`/catalog/admin/episodes/${episodeId}`),
  /** Public lookup: resolves up to 100 ids, but only PUBLISHED titles / episodes of PUBLISHED shows. */
  lookupTitles: (ids: string[]) => get<CatalogLookup>('/catalog/lookup', { ids: ids.slice(0, 100).join(',') }),
  getCatalogStats: () => get<CatalogStats>('/catalog/admin/stats'),
  getGenres: () => get<Genre[]>('/catalog/genres'),
  createGenre: (name: string) => post<Genre>('/catalog/genres', { name }),

  // --- Media ---
  getMediaAssets: () => get<MediaAsset[]>('/media/admin/assets'),
  /** Resolves to null when the title has no video yet (404). */
  getAssetByContent: async (contentId: string): Promise<MediaAsset | null> => {
    try {
      return await get<MediaAsset>(`/media/admin/assets/by-content/${contentId}`);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 404) return null;
      throw err;
    }
  },
  getMediaStats: () => get<MediaStats>('/media/admin/stats'),
  retryTranscode: (assetId: string) => post<MediaAsset>(`/media/admin/assets/${assetId}/retry`),
  deleteMediaAsset: (assetId: string) => del(`/media/admin/assets/${assetId}`),
  getAssetPreview: (contentId: string) => get<MediaPreview>(`/media/admin/assets/${contentId}/preview`),
  importVideo: (contentId: string, url: string) => post<MediaAsset>('/media/admin/imports', { contentId, url }),
  createUpload: (request: CreateUploadRequest) => post<UploadSession>('/media/admin/uploads', request),
  getUpload: (uploadId: string) => get<UploadSession>(`/media/admin/uploads/${uploadId}`),
  /** Sends one raw chunk; goes through the shared client so an expired token is refreshed and the part re-sent. */
  uploadPart: async (
    uploadId: string,
    partNumber: number,
    chunk: Blob,
    signal: AbortSignal,
    onProgress?: (loadedBytes: number) => void
  ) => {
    const res = await api.put<ApiResponse<UploadPartResponse>>(`/media/admin/uploads/${uploadId}/parts/${partNumber}`, chunk, {
      headers: { 'Content-Type': 'application/octet-stream' },
      timeout: 0,
      signal,
      onUploadProgress: (evt) => onProgress?.(evt.loaded),
    });
    return res.data.data;
  },
  completeUpload: async (uploadId: string, signal?: AbortSignal) => {
    const res = await api.post<ApiResponse<UploadCompleteResponse>>(`/media/admin/uploads/${uploadId}/complete`, null, {
      timeout: 0,
      signal,
    });
    return res.data.data;
  },
  abortUpload: (uploadId: string) => del(`/media/admin/uploads/${uploadId}`),
  getMediaFiles: (params: { purpose?: UploadPurpose; page?: number; size?: number }) =>
    get<Page<MediaFile>>('/media/admin/files', params),
  deleteMediaFile: (fileId: string) => del(`/media/admin/files/${fileId}`),

  // --- Subscriptions ---
  getAllPlans: () => get<SubscriptionPlan[]>('/subscriptions/admin/plans'),
  createSubscriptionPlan: (plan: CreatePlanRequest) => post<SubscriptionPlan>('/subscriptions/plans', plan),
  setPlanActive: (planId: string, active: boolean) =>
    patch<SubscriptionPlan>(`/subscriptions/admin/plans/${planId}/active`, null, { active }),
  getSubscriptions: () => get<Subscription[]>('/subscriptions/admin/subscriptions'),
  getAccountSubscription: (accountId: string) => get<Subscription>(`/subscriptions/admin/accounts/${accountId}`),
  getSubscriptionStats: () => get<SubscriptionStats>('/subscriptions/admin/stats'),

  // --- Billing ---
  getTransactions: (accountId?: string) =>
    get<PaymentTransaction[]>('/billing/admin/transactions', accountId ? { accountId } : undefined),
  getBillingStats: () => get<BillingStats>('/billing/admin/stats'),
  /** Record-keeping only: the M-Pesa reversal itself must be done in the Safaricom portal. */
  markTransactionRefunded: (transactionId: string) => post<PaymentTransaction>(`/billing/refund/${transactionId}`),
  getMpesaSettings: () => get<MpesaSettings>('/billing/admin/mpesa-settings'),
  updateMpesaSettings: (settings: MpesaSettingsUpdate) => put<MpesaSettings>('/billing/admin/mpesa-settings', settings),
  clearMpesaSecrets: () => del('/billing/admin/mpesa-settings/secrets'),
  testMpesaSettings: () => post<MpesaTestResult>('/billing/admin/mpesa-settings/test'),

  // --- Devices ---
  getDevices: (accountId?: string) =>
    get<DeviceRegistration[]>('/devices/admin/devices', accountId ? { accountId } : undefined),
  getDeviceStats: () => get<DeviceStats>('/devices/admin/stats'),
  revokeDevice: (deviceId: string) => post<DeviceRegistration>(`/devices/admin/devices/${deviceId}/revoke`),

  // --- Trending ---
  getTopTrending: (limit = 20) => get<TrendingItem[]>('/trending', { limit }),

  // --- Notifications ---
  getNotificationLogs: (accountId?: string) =>
    get<NotificationLog[]>('/notifications/admin/logs', accountId ? { accountId } : undefined),
  getNotificationStats: () => get<NotificationStats>('/notifications/admin/stats'),
  sendNotification: (request: SendNotificationRequest) => post<NotificationLog>('/notifications/send', request),
};

/** Records an admin action in the audit trail without blocking or failing the calling flow. */
export function audit(entry: AuditEntry) {
  adminService.recordAudit(entry).catch((err) => console.warn('Audit log write failed', err));
}
