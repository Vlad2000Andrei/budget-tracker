import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import axiosInstance, { clearAuthToken, setAuthToken } from '../api/axiosInstance';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(null);
  const [user, setUser] = useState(null);
  const [authError, setAuthError] = useState(null);

  // Called by the Google GIS credential callback on the Login page.
  // Exchanges the Google ID token for our backend JWT.
  const login = useCallback(async (googleIdToken) => {
    setAuthError(null);
    try {
      const response = await axiosInstance.post('/v1/tokens', { googleIdToken });
      const { token: jwt } = response.data;

      // Store JWT in module-level Axios interceptor
      setAuthToken(jwt);
      setToken(jwt);

      // Fetch current user profile
      const userResponse = await axiosInstance.get('/v1/users/me');
      setUser(userResponse.data);

      return { success: true };
    } catch (err) {
      setAuthError(err.message);
      return { success: false, error: err.message };
    }
  }, []);

  const logout = useCallback(() => {
    clearAuthToken();
    setToken(null);
    setUser(null);
    setAuthError(null);

    // Revoke Google session so "Sign in with Google" re-prompts
    if (window.google?.accounts?.id) {
      window.google.accounts.id.disableAutoSelect();
    }
  }, []);

  const value = useMemo(
    () => ({ token, user, authError, login, logout, isAuthenticated: !!token }),
    [token, user, authError, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
