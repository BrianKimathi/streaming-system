import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { AuthResponse, AuthState } from '../types';
import api, { SESSION_EXPIRED_EVENT, STORAGE_KEYS, clearSession, storeSession } from '../api/client';

interface AuthContextType extends AuthState {
  login: (auth: AuthResponse) => void;
  logout: () => Promise<void>;
  sessionExpired: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

function readStoredRoles(): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEYS.roles) ?? '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function readStoredState(): AuthState {
  const token = localStorage.getItem(STORAGE_KEYS.token);
  return {
    token,
    accountId: localStorage.getItem(STORAGE_KEYS.accountId),
    email: localStorage.getItem(STORAGE_KEYS.email),
    roles: readStoredRoles(),
    isAuthenticated: !!token,
  };
}

const signedOut: AuthState = { token: null, accountId: null, email: null, roles: [], isAuthenticated: false };

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [authState, setAuthState] = useState<AuthState>(readStoredState);
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    const onExpired = () => {
      setSessionExpired(true);
      setAuthState(signedOut);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const login = useCallback((auth: AuthResponse) => {
    storeSession(auth);
    setSessionExpired(false);
    setAuthState(readStoredState());
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // The local session is cleared regardless; the refresh token expires server-side.
    }
    clearSession();
    setAuthState(signedOut);
  }, []);

  return (
    <AuthContext.Provider value={{ ...authState, login, logout, sessionExpired }}>{children}</AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
