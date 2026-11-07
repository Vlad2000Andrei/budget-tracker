import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import axiosInstance, {
  clearAuthToken,
  getAuthExpiresAt,
  getAuthToken,
  refreshAuthToken,
  setAuthToken,
} from '../api/axiosInstance';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState(null);
  const [authError, setAuthError] = useState(null);

  // Listen for 401 unauthorized events broadcast by axiosInstance interceptor
  useEffect(() => {
    function handleUnauthorized() {
      setIsAuthenticated(false);
      setUser(null);
    }
    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, []);

  // Initialize session on page mount
  useEffect(() => {
    async function initializeAuth() {
      const existingToken = getAuthToken();
      const expiresAt = getAuthExpiresAt();

      // If stored token is already past its expiration, discard it early
      if (existingToken && expiresAt && new Date(expiresAt).getTime() <= Date.now()) {
        clearAuthToken();
      }

      try {
        const userResponse = await axiosInstance.get('/v1/users/me');
        setUser(userResponse.data);
        setIsAuthenticated(true);
      } catch (err) {
        clearAuthToken();
        console.log('No persistent session found:', err.message);
      } finally {
        setLoading(false);
      }
    }
    initializeAuth();
  }, []);

  // Sliding session: periodically refresh token in background when approaching expiration
  useEffect(() => {
    if (!isAuthenticated) return;

    const checkAndRefreshToken = async () => {
      const token = getAuthToken();
      const expiresAt = getAuthExpiresAt();
      if (!token || !expiresAt) return;

      const remainingMs = new Date(expiresAt).getTime() - Date.now();
      if (remainingMs <= 0) return;

      let totalLifespanMs = 8 * 60 * 60 * 1000;
      try {
        const payloadBase64 = token.split('.')[1];
        const payload = JSON.parse(atob(payloadBase64));
        if (payload.exp && payload.iat) {
          totalLifespanMs = (payload.exp - payload.iat) * 1000;
        }
      } catch {
        // Fallback to default
      }

      // Refresh when at most 25% of the token lifespan remains (or 2 hours max)
      const refreshThresholdMs = Math.min(2 * 60 * 60 * 1000, totalLifespanMs * 0.25);
      if (remainingMs <= refreshThresholdMs) {
        try {
          await refreshAuthToken();
        } catch (err) {
          console.warn('Background token refresh failed:', err.message);
        }
      }
    };

    // Initial check
    checkAndRefreshToken();

    // Check every 5 minutes while authenticated
    const intervalId = setInterval(checkAndRefreshToken, 5 * 60 * 1000);

    // Also check on tab focus or visibility change (e.g. laptop wake)
    const handleActivity = () => {
      if (document.visibilityState === 'visible') {
        checkAndRefreshToken();
      }
    };

    window.addEventListener('visibilitychange', handleActivity);
    window.addEventListener('focus', handleActivity);

    return () => {
      clearInterval(intervalId);
      window.removeEventListener('visibilitychange', handleActivity);
      window.removeEventListener('focus', handleActivity);
    };
  }, [isAuthenticated]);

  // Called by the Google GIS credential callback on the Login page.
  // Exchanges the Google ID token for our backend JWT.
  const login = useCallback(async (googleIdToken) => {
    setAuthError(null);
    try {
      const response = await axiosInstance.post('/v1/tokens', { googleIdToken });
      const { token: jwt, expiresAt } = response.data;

      // Store JWT in persistent localStorage & in-memory Axios state
      setAuthToken(jwt, expiresAt);

      // Fetch current user profile
      const userResponse = await axiosInstance.get('/v1/users/me');
      setUser(userResponse.data);
      setIsAuthenticated(true);

      return { success: true };
    } catch (err) {
      setAuthError(err.message);
      return { success: false, error: err.message };
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      // Clear cookie on server
      await axiosInstance.post('/v1/tokens/logout');
    } catch (err) {
      console.error('Failed to log out from server:', err);
    }

    clearAuthToken();
    setIsAuthenticated(false);
    setUser(null);
    setAuthError(null);

    // Revoke Google session so "Sign in with Google" re-prompts
    if (window.google?.accounts?.id) {
      window.google.accounts.id.disableAutoSelect();
    }
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const userResponse = await axiosInstance.get('/v1/users/me');
      setUser(userResponse.data);
    } catch (err) {
      console.error('Failed to refresh user profile:', err);
    }
  }, []);

  const value = useMemo(
    () => ({
      isAuthenticated,
      loading,
      user,
      authError,
      login,
      logout,
      refreshUser,
      refreshToken: refreshAuthToken,
    }),
    [isAuthenticated, loading, user, authError, login, logout, refreshUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
