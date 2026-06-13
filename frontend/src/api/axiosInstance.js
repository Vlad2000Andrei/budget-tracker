import axios from 'axios';

// Axios instance — baseURL is empty because the Vite dev proxy
// handles forwarding /v1/* to the backend at localhost:19092.
const axiosInstance = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Token injector — set by AuthContext after login
let _token = null;

export function setAuthToken(token) {
  _token = token;
}

export function clearAuthToken() {
  _token = null;
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

// Response interceptor: surface error messages cleanly
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message ||
      error.response?.data?.title ||
      error.message ||
      'An unexpected error occurred.';
    return Promise.reject(new Error(message));
  }
);

export default axiosInstance;
