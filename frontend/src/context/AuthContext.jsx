import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import axiosInstance, { clearAuthToken, setAuthToken } from '../api/axiosInstance';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState(null);
  const [authError, setAuthError] = useState(null);

  // Initialize session on page mount
  useEffect(() => {
    async function initializeAuth() {
      try {
        const userResponse = await axiosInstance.get('/v1/users/me');
        setUser(userResponse.data);
        setIsAuthenticated(true);
      } catch (err) {
        console.log('No persistent session found:', err.message);
      } finally {
        setLoading(false);
      }
    }
    initializeAuth();
  }, []);

  // Called by the Google GIS credential callback on the Login page.
  // Exchanges the Google ID token for our backend JWT.
  const login = useCallback(async (googleIdToken) => {
    setAuthError(null);
    try {
      const response = await axiosInstance.post('/v1/tokens', { googleIdToken });
      const { token: jwt } = response.data;

      // Store JWT in module-level Axios interceptor (fallback for non-cookie calls/replays)
      setAuthToken(jwt);

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
    () => ({ isAuthenticated, loading, user, authError, login, logout, refreshUser }),
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
