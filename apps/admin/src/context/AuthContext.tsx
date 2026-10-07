import React, { createContext, useContext, useState, useEffect } from 'react';
import { AuthState } from '../types';

interface AuthContextType extends AuthState {
  login: (token: string, accountId: string, email: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [authState, setAuthState] = useState<AuthState>({
    token: localStorage.getItem('streamx_admin_token'),
    accountId: localStorage.getItem('streamx_admin_account_id'),
    email: localStorage.getItem('streamx_admin_email'),
    roles: ['ROLE_ADMIN', 'ROLE_MANAGER'],
    isAuthenticated: !!localStorage.getItem('streamx_admin_token'),
  });

  const login = (token: string, accountId: string, email: string) => {
    localStorage.setItem('streamx_admin_token', token);
    localStorage.setItem('streamx_admin_account_id', accountId);
    localStorage.setItem('streamx_admin_email', email);
    setAuthState({
      token,
      accountId,
      email,
      roles: ['ROLE_ADMIN', 'ROLE_MANAGER'],
      isAuthenticated: true,
    });
  };

  const logout = () => {
    localStorage.removeItem('streamx_admin_token');
    localStorage.removeItem('streamx_admin_account_id');
    localStorage.removeItem('streamx_admin_email');
    setAuthState({
      token: null,
      accountId: null,
      email: null,
      roles: [],
      isAuthenticated: false,
    });
  };

  return (
    <AuthContext.Provider value={{ ...authState, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
