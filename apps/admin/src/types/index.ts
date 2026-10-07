export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  errors?: any;
  timestamp?: string;
}

export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface Genre {
  id: string;
  name: string;
  description?: string;
}

export interface Movie {
  id: string;
  title: string;
  description: string;
  releaseYear: number;
  durationMinutes: number;
  maturityRating: string;
  genres: Genre[];
  posterUrl: string;
  bannerUrl: string;
  status: ContentStatus;
  createdAt: string;
}

export interface TVShow {
  id: string;
  title: string;
  description: string;
  releaseYear: number;
  maturityRating: string;
  genres: Genre[];
  posterUrl: string;
  bannerUrl: string;
  seasonsCount: number;
  status: ContentStatus;
}

export interface SubscriptionPlan {
  id: string;
  name: string;
  version: number;
  monthlyPrice: number;
  maxConcurrentStreams: number;
  maxRegisteredDevices: number;
  maxResolution: string; // HD, FHD, 4K
  offlineDownloadsAllowed: boolean;
  active: boolean;
}

export interface PaymentTransaction {
  transactionId: string;
  accountId: string;
  amount: number;
  currency: string;
  status: 'SUCCESS' | 'FAILED' | 'REFUNDED' | 'PENDING';
  paymentMethod: string;
  createdAt: string;
}

export interface DeviceRegistration {
  id: string;
  accountId: string;
  deviceFingerprint: string;
  deviceName: string;
  deviceType: 'MOBILE' | 'DESKTOP' | 'SMART_TV' | 'TABLET';
  status: 'ACTIVE' | 'REVOKED';
  registeredAt: string;
  lastActiveAt: string;
}

export interface MediaAsset {
  id: string;
  contentId: string;
  filename: string;
  masterPlaylistUrl: string;
  transcodingStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  durationSeconds: number;
  fileSizeBytes: number;
  uploadedAt: string;
}

export interface TrendingItem {
  contentId: string;
  title: string;
  contentType: 'MOVIE' | 'TV_SHOW' | 'EPISODE';
  views1h: number;
  views6h: number;
  completions24h: number;
  likes24h: number;
  velocityScore: number;
  updatedAt: string;
}

export interface AnalyticsDashboard {
  date: string;
  dailyActiveUsers: number;
  monthlyActiveUsers: number;
  totalWatchTimeHours: number;
  averageCompletionRatePercentage: number;
  totalStreamsStarted: number;
  totalSubscriptionsActive: number;
  totalRevenue: number;
}

export interface NotificationLog {
  id: string;
  accountId: string;
  recipient: string;
  channel: 'EMAIL' | 'SMS' | 'IN_APP';
  template: 'WELCOME' | 'OTP' | 'PAYMENT_SUCCESS' | 'PAYMENT_FAILED';
  subject: string;
  body: string;
  status: 'SENT' | 'FAILED' | 'PENDING';
  createdAt: string;
}

export interface UserAccount {
  id: string;
  email: string;
  phoneNumber?: string;
  roles: string[];
  blocked: boolean;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  timestamp: string;
  administratorId?: string;
  administratorEmail: string;
  role: string;
  action: string;
  targetType: string;
  targetId: string;
  reason: string;
  ipAddress?: string;
  correlationId?: string;
  details?: string;
}

export interface FeatureFlag {
  id: string;
  flagKey: string;
  description: string;
  enabled: boolean;
  targetPercentage: number;
  targetPlan?: string;
  targetCountry?: string;
  lastModifiedBy?: string;
  lastModifiedAt?: string;
}

export interface Incident {
  id: string;
  title: string;
  description: string;
  severity: 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';
  status: 'OPEN' | 'INVESTIGATING' | 'MITIGATED' | 'RESOLVED' | 'CLOSED';
  affectedServices: string;
  createdBy: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface SupportTicket {
  id: string;
  accountId: string;
  userEmail: string;
  category: 'ACCOUNT' | 'BILLING' | 'SUBSCRIPTION' | 'PLAYBACK' | 'DEVICE';
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
  subject: string;
  body: string;
  assignedAgentEmail?: string;
  createdAt: string;
}

export interface PromotionCoupon {
  code: string;
  discountPercentage: number;
  trialDays: number;
  usageLimit: number;
  usedCount: number;
  expiryDate: string;
  eligiblePlan: string;
}

export interface AuthState {
  token: string | null;
  accountId: string | null;
  email: string | null;
  roles: string[];
  isAuthenticated: boolean;
}
