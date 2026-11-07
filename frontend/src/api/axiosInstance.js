import axios from 'axios';

const TOKEN_STORAGE_KEY = 'budget_auth_token';
const EXPIRES_AT_STORAGE_KEY = 'budget_auth_expires_at';

// Axios instance — baseURL is empty because the Vite dev proxy
// handles forwarding /v1/* to the backend at localhost:19092.
const axiosInstance = axios.create({
  baseURL: '',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

let _token = null;
let _expiresAt = null;

// Initialize token & expiry from persistent storage on module load
try {
  _token = localStorage.getItem(TOKEN_STORAGE_KEY);
  _expiresAt = localStorage.getItem(EXPIRES_AT_STORAGE_KEY);
} catch {
  // localStorage may be inaccessible (private browsing or security policies)
}

export function getAuthToken() {
  return _token;
}

export function getAuthExpiresAt() {
  return _expiresAt;
}

export function setAuthToken(token, expiresAt = null) {
  _token = token;
  _expiresAt = expiresAt;
  try {
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
      if (expiresAt) {
        localStorage.setItem(EXPIRES_AT_STORAGE_KEY, expiresAt);
      }
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(EXPIRES_AT_STORAGE_KEY);
    }
  } catch {
    // Ignore storage write errors
  }
}

export function clearAuthToken() {
  _token = null;
  _expiresAt = null;
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(EXPIRES_AT_STORAGE_KEY);
  } catch {
    // Ignore storage removal errors
  }
}

// Proactive or on-demand token refresh
let _refreshPromise = null;

export async function refreshAuthToken() {
  if (_refreshPromise) {
    return _refreshPromise;
  }

  _refreshPromise = (async () => {
    try {
      const response = await axios.post('/v1/tokens/refresh', {}, {
        headers: _token ? { Authorization: `Bearer ${_token}` } : {},
        withCredentials: true,
      });
      const { token: newJwt, expiresAt: newExpiresAt } = response.data;
      setAuthToken(newJwt, newExpiresAt);
      return response.data;
    } finally {
      _refreshPromise = null;
    }
  })();

  return _refreshPromise;
}

// Request interceptor: attach JWT to every request if present
axiosInstance.interceptors.request.use(
  (config) => {
    if (_token) {
      config.headers['Authorization'] = `Bearer ${_token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: surface error messages cleanly and handle 401 unauth
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url || '';

    // If 401 received on an authenticated request (skip login/logout endpoints),
    // clear local auth and broadcast unauthorized event to cleanly route to login
    if (status === 401 && !url.includes('/v1/tokens')) {
      clearAuthToken();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('auth:unauthorized'));
      }
    }

    const message =
      error.response?.data?.message ||
      error.response?.data?.title ||
      error.message ||
      'An unexpected error occurred.';
    return Promise.reject(new Error(message));
  }
);

export default axiosInstance;
