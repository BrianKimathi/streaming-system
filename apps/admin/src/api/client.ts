import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse, AuthResponse } from '../types';

const metaEnv = (import.meta as unknown as { env?: { VITE_API_URL?: string } }).env;
export const API_BASE_URL = metaEnv?.VITE_API_URL || 'https://streamxapi.briankimathi.dev/api/v1';

export const STORAGE_KEYS = {
  token: 'streamx_admin_token',
  refreshToken: 'streamx_admin_refresh_token',
  accountId: 'streamx_admin_account_id',
  email: 'streamx_admin_email',
  roles: 'streamx_admin_roles',
} as const;

export const SESSION_EXPIRED_EVENT = 'streamx:session-expired';

export function storeSession(auth: AuthResponse) {
  localStorage.setItem(STORAGE_KEYS.token, auth.accessToken);
  localStorage.setItem(STORAGE_KEYS.refreshToken, auth.refreshToken);
  localStorage.setItem(STORAGE_KEYS.accountId, auth.accountId);
  localStorage.setItem(STORAGE_KEYS.email, auth.email ?? '');
  localStorage.setItem(STORAGE_KEYS.roles, JSON.stringify(auth.roles ?? []));
}

export function clearSession() {
  Object.values(STORAGE_KEYS).forEach((key) => localStorage.removeItem(key));
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(STORAGE_KEYS.token);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem(STORAGE_KEYS.refreshToken);
  if (!refreshToken) return null;
  try {
    const res = await axios.post<ApiResponse<AuthResponse>>(`${API_BASE_URL}/auth/refresh`, { refreshToken });
    const auth = res.data.data;
    if (!auth?.accessToken) return null;
    storeSession(auth);
    return auth.accessToken;
  } catch {
    return null;
  }
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    const url = original?.url ?? '';
    const isAuthCall = url.includes('/auth/admin/login') || url.includes('/auth/refresh');

    if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      refreshInFlight = refreshInFlight ?? refreshAccessToken().finally(() => (refreshInFlight = null));
      const newToken = await refreshInFlight;
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original);
      }
      clearSession();
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  }
);

/** Extracts the most useful human-readable message from an API error. */
export function errorMessage(err: unknown, fallback = 'Request failed'): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as Partial<ApiResponse<unknown>> | undefined;
    if (body?.errors && typeof body.errors === 'object') {
      const details = Object.entries(body.errors as Record<string, string>)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join('; ');
      if (details) return `${body.message ?? fallback} (${details})`;
    }
    if (body?.message) return body.message;
    if (err.response) return `${fallback} (HTTP ${err.response.status})`;
    return `${fallback}: cannot reach the API (${err.message})`;
  }
  if (err instanceof Error) return err.message;
  return fallback;
}

export default api;
