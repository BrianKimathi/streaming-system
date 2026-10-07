import { API_BASE_URL } from '../api/client';

const API_ORIGIN = (() => {
  try {
    return new URL(API_BASE_URL, window.location.origin).origin;
  } catch {
    return '';
  }
})();

/** Media URLs may come back as API-relative paths ("/api/v1/media/..."); resolve them against the API origin. */
export function resolveApiUrl(path: string): string {
  return /^https?:\/\//i.test(path) ? path : `${API_ORIGIN}${path.startsWith('/') ? '' : '/'}${path}`;
}

export function isHttpUrl(value: string): boolean {
  const trimmed = value.trim();
  if (!/^https?:\/\/\S+$/i.test(trimmed)) return false;
  try {
    const url = new URL(trimmed);
    return (url.protocol === 'http:' || url.protocol === 'https:') && !!url.hostname;
  } catch {
    return false;
  }
}

/** Catalog links must be blank or an http(s) URL of at most 2000 characters. */
export function linkError(label: string, value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  if (trimmed.length > 2000) return `${label} must be at most 2000 characters.`;
  return isHttpUrl(trimmed) ? null : `${label} must be an http(s) link.`;
}

/** True for files served by our own media service (as opposed to external links). */
export function isMediaServiceUrl(value: string): boolean {
  return /\/api\/v1\/media\/files\//.test(value);
}
