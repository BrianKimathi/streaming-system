import React, { useEffect, useState } from 'react';
import { Shield, Globe, KeyRound, LogOut, Loader2, Info, User, Wallet } from 'lucide-react';
import { API_BASE_URL, STORAGE_KEYS } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { formatDateTime, humanizeRole } from '../utils/format';

interface TokenTimes {
  issuedAt: number | null;
  expiresAt: number | null;
}

/** Reads `iat`/`exp` (seconds since epoch) from a JWT payload without verifying it. */
function decodeTokenTimes(token: string | null): TokenTimes | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const claims: unknown = JSON.parse(atob(padded));
    if (typeof claims !== 'object' || claims === null) return null;
    const { exp, iat } = claims as { exp?: unknown; iat?: unknown };
    return {
      expiresAt: typeof exp === 'number' ? exp * 1000 : null,
      issuedAt: typeof iat === 'number' ? iat * 1000 : null,
    };
  } catch {
    return null;
  }
}

function describeRemaining(ms: number): string {
  if (ms <= 0) return 'expired';
  const totalMinutes = Math.floor(ms / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours > 0) return `${hours}h ${minutes}m remaining`;
  if (totalMinutes > 0) return `${totalMinutes}m remaining`;
  return 'less than a minute remaining';
}

const Field: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1 py-3 border-b border-slate-800 last:border-b-0">
    <dt className="text-xs font-semibold text-slate-400">{label}</dt>
    <dd className="text-xs text-white sm:text-right break-all">{children}</dd>
  </div>
);

export const SettingsPage: React.FC<{ onNavigate?: (tab: string) => void }> = ({ onNavigate }) => {
  const { token, accountId, email, roles, logout } = useAuth();
  const [now, setNow] = useState(() => Date.now());
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(interval);
  }, []);

  // The API client refreshes the access token in storage, so prefer the stored token over the context copy.
  const currentToken = localStorage.getItem(STORAGE_KEYS.token) ?? token;
  const times = decodeTokenTimes(currentToken);

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await logout();
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="border-b border-slate-800 pb-4">
        <h3 className="text-lg font-bold text-white flex items-center gap-2">
          <Shield className="w-5 h-5 text-red-500" />
          Session & Environment
        </h3>
        <p className="text-xs text-slate-400">Read-only details about your admin session and the API this console talks to.</p>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2 mb-2">
          <User className="w-4 h-4 text-red-500" />
          Signed-in administrator
        </h4>
        <dl>
          <Field label="Email">{email || 'unknown'}</Field>
          <Field label="Account ID">
            <span className="font-mono">{accountId || 'unknown'}</span>
          </Field>
          <Field label="Roles">
            {roles.length === 0 ? (
              'none'
            ) : (
              <span className="inline-flex flex-wrap gap-1 sm:justify-end">
                {roles.map((role) => (
                  <span
                    key={role}
                    className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-[10px] font-medium text-slate-300"
                  >
                    {humanizeRole(role)}
                  </span>
                ))}
              </span>
            )}
          </Field>
        </dl>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2 mb-2">
          <KeyRound className="w-4 h-4 text-amber-400" />
          Access token
        </h4>
        <dl>
          <Field label="Issued at">
            {times?.issuedAt ? formatDateTime(new Date(times.issuedAt).toISOString()) : 'unknown'}
          </Field>
          <Field label="Expires at">
            {times?.expiresAt ? (
              <>
                {formatDateTime(new Date(times.expiresAt).toISOString())}
                <span
                  className={`block text-[11px] ${times.expiresAt - now <= 0 ? 'text-rose-400' : 'text-slate-500'}`}
                >
                  {describeRemaining(times.expiresAt - now)}
                </span>
              </>
            ) : (
              'unknown'
            )}
          </Field>
        </dl>
        <p className="mt-3 text-[11px] text-slate-500">
          Expired access tokens are refreshed automatically on the next API call while your refresh token is valid.
        </p>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2 mb-2">
          <Globe className="w-4 h-4 text-blue-400" />
          Environment
        </h4>
        <dl>
          <Field label="API base URL">
            <span className="font-mono">{API_BASE_URL}</span>
          </Field>
        </dl>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <Wallet className="w-4 h-4 text-emerald-400" />
            Payments (M-Pesa)
          </h4>
          <p className="text-xs text-slate-400 mt-1">
            Daraja consumer key, secret, passkey, shortcode and callback URL used for subscription checkout.
          </p>
        </div>
        <button
          onClick={() => onNavigate?.('payments')}
          className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-semibold transition"
        >
          Open payment settings
        </button>
      </div>

      <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-start gap-3">
        <Info className="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
        <p className="text-xs text-slate-400 leading-relaxed">
          Platform configuration such as the JWT signing key, SMTP credentials and admin bootstrap credentials lives in
          server environment variables. It is intentionally not exposed to or editable from the browser. M-Pesa credentials are
          the exception: they are managed on the Payments page, encrypted on the server and never sent back to the browser.
        </p>
      </div>

      <div className="flex justify-end">
        <button
          onClick={() => void handleSignOut()}
          disabled={signingOut}
          className="px-5 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-xl font-bold text-xs shadow-md transition flex items-center gap-2 disabled:opacity-50"
        >
          {signingOut ? <Loader2 className="w-4 h-4 animate-spin" /> : <LogOut className="w-4 h-4" />}
          Sign out
        </button>
      </div>
    </div>
  );
};
