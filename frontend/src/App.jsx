import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/Login/LoginPage';
import DashboardPage from './pages/Dashboard/DashboardPage';
import GoalsPage from './pages/Goals/GoalsPage';
import AccountsPage from './pages/Accounts/AccountsPage';
import SettingsPage from './pages/Settings/SettingsPage';

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
  const { isAuthenticated } = useAuth();

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
        <Route path="goals" element={<GoalsPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="settings" element={<SettingsPage />} />
      </Route>

      {/* Catch-all */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
