import { useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/Login/LoginPage';
import DashboardPage from './pages/Dashboard/DashboardPage';
import GoalsPage from './pages/Goals/GoalsPage';
import AccountsPage from './pages/Accounts/AccountsPage';
import SettingsPage from './pages/Settings/SettingsPage';
import CategoriesPage from './pages/Categories/CategoriesPage';
import StatsPage from './pages/Stats/StatsPage';
import ImportPage from './pages/Import/ImportPage';

import WelcomePage from './pages/Welcome/WelcomePage';


function ProtectedRouteMain({ children }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (user === null) {
    return (
      <div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--md-background)' }}>
        <span style={{ fontSize: 'var(--md-body-large-size)', color: 'var(--md-on-background)' }}>Loading profile...</span>
      </div>
    );
  }
  if (!user.isOnboarded) return <Navigate to="/welcome" replace />;
  return children;
}

function ProtectedRouteWelcome({ children }) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (user === null) {
    return (
      <div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--md-background)' }}>
        <span style={{ fontSize: 'var(--md-body-large-size)', color: 'var(--md-on-background)' }}>Loading profile...</span>
      </div>
    );
  }
  if (user.isOnboarded) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  const { isAuthenticated, loading } = useAuth();

  useEffect(() => {
    const theme = localStorage.getItem('theme') || 'light';
    document.documentElement.setAttribute('data-theme', theme);
  }, []);

  if (loading) {
    return (
      <div style={{ display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center', backgroundColor: 'var(--md-background)' }}>
        <span style={{ fontSize: 'var(--md-body-large-size)', color: 'var(--md-on-background)' }}>Loading application...</span>
      </div>
    );
  }

  return (
    <Routes>
      {/* Public */}
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />}
      />

      {/* Welcome / Onboarding */}
      <Route
        path="/welcome"
        element={
          <ProtectedRouteWelcome>
            <WelcomePage />
          </ProtectedRouteWelcome>
        }
      />

      {/* Protected shell */}
      <Route
        element={
          <ProtectedRouteMain>
            <AppLayout />
          </ProtectedRouteMain>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="stats" element={<StatsPage />} />
        <Route path="goals" element={<GoalsPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="categories" element={<CategoriesPage />} />
        <Route path="import" element={<ImportPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>

      {/* Catch-all */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
