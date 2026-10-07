import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'https://streamxapi.briankimathi.dev/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('streamx_admin_token');
    const accountId = localStorage.getItem('streamx_admin_account_id');

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    if (accountId) {
      config.headers['X-Account-Id'] = accountId;
      config.headers['X-User-Roles'] = 'ROLE_ADMIN,ROLE_MANAGER';
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('streamx_admin_token');
      localStorage.removeItem('streamx_admin_account_id');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
