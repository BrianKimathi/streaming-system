export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errors?: unknown;
  timestamp?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// --- Auth ---
export interface AuthResponse {
  accountId: string;
  email: string;
  phoneNumber?: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  roles: string[];
  accessToken: string;
  refreshToken: string;
}

export interface AuthState {
  token: string | null;
  accountId: string | null;
  email: string | null;
  roles: string[];
  isAuthenticated: boolean;
}

export interface UserAccount {
  id: string;
  email: string | null;
  phoneNumber: string | null;
  roles: string[];
  status: 'ACTIVE' | 'SUSPENDED' | 'DELETED' | string;
  blocked: boolean;
  emailVerified: boolean;
  phoneVerified: boolean;
  createdAt: string;
}

export interface DailyCount {
  date: string;
  count: number;
}

export interface DailyAmount {
  date: string;
  amount: number;
}

export interface AccountStats {
  totalAccounts: number;
  activeAccounts: number;
  suspendedAccounts: number;
  newAccountsToday: number;
  newAccountsLast7Days: number;
  newAccountsLast30Days: number;
  dailySignups: DailyCount[];
}

// --- Catalog ---
export type ContentStatus =
  | 'DRAFT'
  | 'PROCESSING'
  | 'READY'
  | 'SCHEDULED'
  | 'PUBLISHED'
  | 'EXPIRING'
  | 'EXPIRED'
  | 'ARCHIVED';

export const CONTENT_STATUSES: ContentStatus[] = [
  'DRAFT',
  'PROCESSING',
  'READY',
  'SCHEDULED',
  'PUBLISHED',
  'EXPIRING',
  'EXPIRED',
  'ARCHIVED',
];

export interface Genre {
  id: string;
  name: string;
  slug: string;
}

export interface Movie {
  id: string;
  title: string;
  synopsis: string | null;
  releaseDate: string | null;
  runtimeMinutes: number | null;
  maturityRating: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  trailerUrl: string | null;
  mediaAssetUrl: string | null;
  status: ContentStatus;
  genres: Genre[];
  createdAt: string | null;
}

export interface CreateMovieRequest {
  title: string;
  synopsis?: string;
  releaseDate?: string;
  runtimeMinutes?: number;
  maturityRating?: string;
  posterUrl?: string;
  backdropUrl?: string;
  trailerUrl?: string;
  status?: ContentStatus;
  genreIds?: string[];
}

/**
 * Body for PUT /catalog/admin/movies/{id}: replaces the movie's details, so cleared optional fields are sent as null.
 */
export interface UpdateMovieRequest {
  title: string;
  synopsis: string | null;
  releaseDate: string | null;
  runtimeMinutes: number | null;
  maturityRating: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  trailerUrl: string | null;
  status: ContentStatus;
  genreIds: string[];
}

export interface TVShow {
  id: string;
  title: string;
  synopsis: string | null;
  releaseDate: string | null;
  maturityRating: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  trailerUrl: string | null;
  status: ContentStatus;
  genres: Genre[];
  seasonsCount: number;
  createdAt: string | null;
}

export interface CreateTvShowRequest {
  title: string;
  synopsis?: string;
  releaseDate?: string;
  maturityRating?: string;
  posterUrl?: string;
  backdropUrl?: string;
  trailerUrl?: string;
  status?: ContentStatus;
  genreIds?: string[];
  seasonsCount?: number;
}

/** Body for PUT /catalog/admin/tv-shows/{id}: replaces the show's details (seasons are managed separately). */
export interface UpdateTvShowRequest {
  title: string;
  synopsis: string | null;
  releaseDate: string | null;
  maturityRating: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  trailerUrl: string | null;
  status: ContentStatus;
  genreIds: string[];
}

export interface Episode {
  id: string;
  seasonId: string;
  tvShowId: string;
  seasonNumber: number;
  episodeNumber: number;
  title: string;
  synopsis: string | null;
  runtimeMinutes: number | null;
  releaseDate: string | null;
  thumbnailUrl: string | null;
}

export interface Season {
  id: string;
  tvShowId: string;
  seasonNumber: number;
  title: string | null;
  synopsis: string | null;
  releaseDate: string | null;
  posterUrl: string | null;
  episodes: Episode[] | null;
}

export interface TvShowDetail extends TVShow {
  seasons: Season[] | null;
}

/** Body for POST /catalog/admin/tv-shows/{showId}/seasons and PUT /catalog/admin/seasons/{id}. */
export interface SeasonRequest {
  seasonNumber: number;
  title?: string | null;
  synopsis?: string | null;
  releaseDate?: string | null;
  posterUrl?: string | null;
}

/** Body for POST /catalog/admin/seasons/{seasonId}/episodes and PUT /catalog/admin/episodes/{id}. */
export interface EpisodeRequest {
  episodeNumber: number;
  title: string;
  synopsis?: string | null;
  runtimeMinutes?: number | null;
  releaseDate?: string | null;
  thumbnailUrl?: string | null;
}

/** GET /catalog/lookup — only PUBLISHED titles (and episodes of PUBLISHED shows) are returned. */
export interface CatalogLookup {
  movies: Movie[];
  tvShows: TVShow[];
  episodes: Episode[];
}

export interface CatalogStats {
  totalMovies: number;
  publishedMovies: number;
  totalTvShows: number;
  publishedTvShows: number;
  totalGenres: number;
  moviesByStatus: Record<string, number>;
  tvShowsByStatus: Record<string, number>;
}

// --- Subscriptions ---
export type BillingInterval = 'MONTHLY' | 'YEARLY';
export type VideoResolution = 'SD_720P' | 'FHD_1080P' | 'UHD_4K';

export interface SubscriptionPlan {
  id: string;
  name: string;
  description: string | null;
  price: number;
  currency: string;
  billingInterval: BillingInterval;
  version: number;
  active: boolean;
  maxProfiles: number;
  maxRegisteredDevices: number;
  maxConcurrentStreams: number;
  maxResolution: VideoResolution;
  hdrEnabled: boolean;
  audioQuality: string;
  downloadsEnabled: boolean;
  maxDownloadDevices: number;
  kidsProfilesEnabled: boolean;
}

export type CreatePlanRequest = Omit<SubscriptionPlan, 'id' | 'version' | 'active'>;

export interface Entitlements {
  subscriptionId: string;
  status: string;
  maxProfiles: number;
  maxRegisteredDevices: number;
  maxConcurrentStreams: number;
  maxResolution: VideoResolution;
  hdrEnabled: boolean;
  audioQuality: string;
  downloadsEnabled: boolean;
  maxDownloadDevices: number;
  kidsProfilesEnabled: boolean;
}

export interface Subscription {
  id: string;
  accountId: string;
  plan: SubscriptionPlan;
  status: 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'GRACE_PERIOD' | 'CANCELLED' | 'EXPIRED' | 'SUSPENDED';
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  entitlements: Entitlements;
}

export interface SubscriptionStats {
  totalSubscriptions: number;
  activeSubscriptions: number;
  newLast30Days: number;
  countByStatus: Record<string, number>;
  activeByPlan: {
    planId: string;
    planName: string;
    planVersion: number | null;
    price: number | null;
    currency: string | null;
    activeSubscriptions: number;
  }[];
  monthlyRecurringRevenueByCurrency: Record<string, number>;
  activePlans: number;
}

// --- Billing ---
export type PaymentStatus = 'INITIATED' | 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED' | 'CANCELLED';

export const PAYMENT_STATUSES: PaymentStatus[] = ['INITIATED', 'PENDING', 'COMPLETED', 'FAILED', 'REFUNDED', 'CANCELLED'];

export interface PaymentTransaction {
  id: string;
  accountId: string;
  subscriptionId: string | null;
  planId: string | null;
  planName: string | null;
  amount: number;
  currency: string;
  status: PaymentStatus;
  /** "MPESA" for all new transactions; older records may carry other values. */
  paymentMethod: 'MPESA' | string | null;
  /** Full MSISDN on admin endpoints (e.g. 254712345678). */
  phoneNumber: string | null;
  /** M-Pesa receipt number (MpesaReceiptNumber) once the payment is confirmed. */
  externalTransactionId: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface BillingStats {
  totalTransactions: number;
  completedTransactions: number;
  failedTransactions: number;
  refundedTransactions: number;
  payingAccounts: number;
  revenueByCurrency: Record<string, number>;
  refundedByCurrency: Record<string, number>;
  totalRevenue: number;
  revenueLast30Days: number;
  primaryCurrency: string;
  countByStatus: Record<string, number>;
  dailyRevenue: DailyAmount[];
}

// --- Devices ---
export interface DeviceRegistration {
  id: string;
  accountId: string;
  deviceFingerprint: string;
  deviceName: string | null;
  deviceType: 'TV' | 'PHONE' | 'TABLET' | 'LAPTOP' | 'CONSOLE' | string;
  platform: string | null;
  appVersion: string | null;
  status: 'ACTIVE' | 'REVOKED';
  registeredAt: string | null;
  lastSeenAt: string | null;
}

export interface DeviceStats {
  totalDevices: number;
  activeDevices: number;
  revokedDevices: number;
  activeDevicesByType: Record<string, number>;
}

// --- Media ---
export type MediaProcessingStatus = 'UPLOADING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface MediaAsset {
  id: string;
  contentId: string;
  originalFilename: string | null;
  masterPlaylistUrl: string | null;
  status: MediaProcessingStatus;
  durationSeconds: number | null;
  fileSizeBytes: number | null;
  failureReason: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  /** Link the video was imported from, if it was imported rather than uploaded. */
  sourceUrl?: string | null;
  /** 0–100 while PROCESSING (transcoding); null otherwise. */
  progressPercent?: number | null;
}

export interface MediaStats {
  totalAssets: number;
  countByStatus: Record<string, number>;
  totalReadyDurationSeconds: number;
  totalSourceBytes: number;
}

export type UploadPurpose = 'VIDEO' | 'TRAILER' | 'IMAGE';

export interface CreateUploadRequest {
  filename: string;
  contentType: string;
  sizeBytes: number;
  purpose: UploadPurpose;
  /** Required for VIDEO: the movie or episode id. */
  contentId?: string;
}

export interface UploadSession {
  uploadId: string;
  purpose: UploadPurpose;
  contentId: string | null;
  filename: string;
  sizeBytes: number;
  chunkSizeBytes: number;
  totalParts: number;
  receivedParts: number[];
  status: 'OPEN' | 'COMPLETED' | 'ABORTED';
  createdAt: string;
}

export interface UploadPartResponse {
  partNumber: number;
  sizeBytes: number;
  receivedParts: number[];
}

/** A public image or trailer stored by the media service. */
export interface MediaFile {
  id: string;
  purpose: UploadPurpose;
  filename: string;
  contentType: string;
  sizeBytes: number;
  url: string;
  createdAt: string;
}

export interface UploadCompleteResponse {
  purpose: UploadPurpose;
  file: MediaFile | null;
  asset: MediaAsset | null;
}

export interface MediaPreview {
  streamUrl: string;
}

// --- Payments settings (M-Pesa Daraja) ---
export type MpesaEnvironment = 'sandbox' | 'production';
export type MpesaTransactionType = 'CustomerPayBillOnline' | 'CustomerBuyGoodsOnline';
export type SettingSource = 'DATABASE' | 'ENVIRONMENT' | 'NONE';

export type MpesaSettingField =
  | 'environment'
  | 'shortcode'
  | 'transactionType'
  | 'callbackBaseUrl'
  | 'consumerKey'
  | 'consumerSecret'
  | 'passkey'
  | 'callbackToken';

export interface MpesaSettings {
  environment: MpesaEnvironment | null;
  shortcode: string | null;
  transactionType: MpesaTransactionType | null;
  callbackBaseUrl: string | null;
  consumerKeySet: boolean;
  /** "••••" + last 4 characters of the stored consumer key, or null. */
  consumerKeyHint: string | null;
  consumerSecretSet: boolean;
  passkeySet: boolean;
  callbackTokenSet: boolean;
  configured: boolean;
  sources: Partial<Record<MpesaSettingField, SettingSource>>;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface MpesaSettingsUpdate {
  environment: MpesaEnvironment;
  shortcode: string;
  transactionType: MpesaTransactionType;
  callbackBaseUrl: string;
  /** Null or blank keeps the stored value. */
  consumerKey?: string | null;
  consumerSecret?: string | null;
  passkey?: string | null;
  regenerateCallbackToken?: boolean;
}

export interface MpesaTestResult {
  ok: boolean;
  message: string;
}

// --- Trending ---
export interface TrendingItem {
  contentId: string;
  title: string;
  contentType: string;
  views1h: number;
  views6h: number;
  completions24h: number;
  likes24h: number;
  velocityScore: number;
  updatedAt: string;
}

// --- Playback / Watch history ---
export interface PlaybackStats {
  activeStreams: number;
  totalSessions: number;
  sessionsToday: number;
  dailyActiveAccounts: number;
  weeklyActiveAccounts: number;
  monthlyActiveAccounts: number;
  dailySessions: DailyCount[];
  topContentLast7Days: { contentId: string; sessions: number }[];
}

export interface WatchHistoryStats {
  totalProgressRecords: number;
  completedViews: number;
  completionRate: number;
  averageProgressPercent: number;
  totalWatchSeconds: number;
  watchSecondsLast7Days: number;
  activeViewersLast7Days: number;
  activityLast24Hours: number;
  topContent: { contentId: string; viewers: number; completions: number }[];
}

// --- Notifications ---
export type NotificationChannel = 'EMAIL' | 'SMS' | 'IN_APP';

export interface NotificationLog {
  id: string;
  accountId: string;
  recipient: string;
  channel: NotificationChannel | string;
  template: string;
  subject: string | null;
  body: string | null;
  status: 'SENT' | 'DELIVERED' | 'FAILED' | 'PENDING' | string;
  failureReason: string | null;
  createdAt: string;
}

export interface NotificationStats {
  total: number;
  last24Hours: number;
  countByStatus: Record<string, number>;
  countByChannel: Record<string, number>;
}

export interface SendNotificationRequest {
  accountId: string;
  recipient: string;
  channel: NotificationChannel;
  template?: string;
  subject?: string;
  body: string;
}

// --- Analytics overview ---
export interface DashboardSection<T> {
  available: boolean;
  data?: T;
  error?: string;
}

export interface PlatformDashboard {
  generatedAt: string;
  sections: {
    users: DashboardSection<AccountStats>;
    catalog: DashboardSection<CatalogStats>;
    subscriptions: DashboardSection<SubscriptionStats>;
    billing: DashboardSection<BillingStats>;
    devices: DashboardSection<DeviceStats>;
    media: DashboardSection<MediaStats>;
    playback: DashboardSection<PlaybackStats>;
    watchHistory: DashboardSection<WatchHistoryStats>;
    notifications: DashboardSection<NotificationStats>;
  };
}

// --- Admin control plane ---
export interface AuditLog {
  id: string;
  timestamp: string;
  administratorId?: string;
  administratorEmail: string;
  role: string;
  action: string;
  targetType: string;
  targetId: string | null;
  reason: string | null;
  ipAddress?: string | null;
  correlationId?: string | null;
  details?: string | null;
}

export interface AuditEntry {
  action: string;
  targetType: string;
  targetId?: string;
  reason?: string;
  details?: string;
}

export interface FeatureFlag {
  id: string;
  flagKey: string;
  description: string | null;
  enabled: boolean;
  targetPercentage: number;
  targetPlan?: string | null;
  targetCountry?: string | null;
  lastModifiedBy?: string | null;
  lastModifiedAt?: string | null;
}

export type IncidentSeverity = 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';
export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'MITIGATED' | 'RESOLVED' | 'CLOSED';

export interface Incident {
  id: string;
  title: string;
  description: string | null;
  severity: IncidentSeverity;
  status: IncidentStatus;
  affectedServices: string | null;
  createdBy: string | null;
  createdAt: string;
  resolvedAt?: string | null;
}

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface SupportTicket {
  id: string;
  accountId: string | null;
  userEmail: string | null;
  category: string;
  priority: TicketPriority;
  status: TicketStatus;
  subject: string;
  body: string | null;
  assignedAgentEmail?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ComponentHealth {
  name: string;
  status: 'UP' | 'DOWN';
  latencyMs?: number;
  httpStatus?: number;
  error?: string;
  connections?: number;
  maxConnections?: number;
  version?: string;
}

export interface SystemHealth {
  status: 'UP' | 'DEGRADED';
  componentsDown: number;
  checkedAt: string;
  services: ComponentHealth[];
  infrastructure: ComponentHealth[];
}
