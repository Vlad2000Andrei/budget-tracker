import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import styles from './Sidebar.module.css';

const NAV_ITEMS = [
  {
    to: '/',
    end: true,
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" />
      </svg>
    ),
    label: 'Dashboard',
  },
  {
    to: '/stats',
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z" />
      </svg>
    ),
    label: 'Statistics',
  },
  {
    to: '/goals',
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm0-14c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm0 10c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4zm0-6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
        <path d="M21 3h-5l1.8 1.8-6.2 6.2 1.4 1.4 6.2-6.2L21 8V3z"/>
      </svg>
    ),
    label: 'Goals',
  },
  {
    to: '/accounts',
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M4 4h16v2H4V4zm0 14h16v2H4v-2zm0-7h16v2H4v-2zm12 3.5L21.5 12 16 9.5V13h-4v2h4v2.5z" />
      </svg>
    ),
    label: 'Accounts',
  },
  {
    to: '/categories',
    icon: (
      <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <path d="M17.63 5.84C17.27 5.33 16.67 5 16 5L5 5.01C3.9 5.01 3 5.9 3 7v10c0 1.1.9 1.99 2 1.99L16 19c.67 0 1.27-.33 1.63-.84L22 12l-4.37-6.16z"/>
      </svg>
    ),
    label: 'Categories',
  },
];

export default function Sidebar({ onCollapse, collapsed }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleNavClick = (e) => {
    if (window.hasUnsavedImportChanges) {
      const confirmLeave = window.confirm(
        'You have unsaved imported transactions. Are you sure you want to leave? Your changes will be lost.'
      );
      if (!confirmLeave) {
        e.preventDefault();
      } else {
        window.hasUnsavedImportChanges = false;
      }
    }
  };

  const handleLogout = () => {
    if (window.hasUnsavedImportChanges) {
      const confirmLeave = window.confirm(
        'You have unsaved imported transactions. Are you sure you want to leave? Your changes will be lost.'
      );
      if (!confirmLeave) return;
      window.hasUnsavedImportChanges = false;
    }
    logout();
    navigate('/login', { replace: true });
  };

  const avatarLetter = user?.displayName
    ? user.displayName[0].toUpperCase()
    : user?.email
    ? user.email[0].toUpperCase()
    : '?';

  return (
    <nav className={`${styles.sidebar} ${collapsed ? styles.collapsed : ''}`} aria-label="Main navigation">
      {/* Logo */}
      <div className={styles.logo}>
        <span className={styles.logoIcon} aria-hidden="true">💰</span>
        {!collapsed && <span className={styles.logoText}>Budget Tracker</span>}
      </div>

      {/* Primary nav */}
      <ul className={styles.navList} role="list">
        {NAV_ITEMS.map(({ to, end, icon, label }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              onClick={handleNavClick}
              className={({ isActive }) =>
                `${styles.navItem} ${isActive ? styles.active : ''}`
              }
              title={collapsed ? label : undefined}
            >
              <span className={styles.navIcon}>{icon}</span>
              {!collapsed && <span className={styles.navLabel}>{label}</span>}
              {({ isActive }) => isActive && <span className={styles.indicator} aria-hidden="true" />}
            </NavLink>
          </li>
        ))}
      </ul>

      <div className={styles.divider} />

      {/* Settings */}
      <ul className={styles.navList} role="list">
        <li>
          <NavLink
            to="/settings"
            onClick={handleNavClick}
            className={({ isActive }) =>
              `${styles.navItem} ${isActive ? styles.active : ''}`
            }
            title={collapsed ? 'Settings' : undefined}
          >
            <span className={styles.navIcon}>
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7 7 0 0 0-1.62-.94l-.36-2.54A.484.484 0 0 0 14 2h-4a.484.484 0 0 0-.48.41l-.36 2.54a7.36 7.36 0 0 0-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87a.47.47 0 0 0 .12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.36 1.04.67 1.62.94l.36 2.54c.05.24.27.41.49.41h4c.22 0 .44-.17.47-.41l.36-2.54a7.36 7.36 0 0 0 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.47.47 0 0 0-.12-.61l-2.01-1.58zM12 15.6a3.6 3.6 0 1 1 0-7.2 3.6 3.6 0 0 1 0 7.2z" />
              </svg>
            </span>
            {!collapsed && <span className={styles.navLabel}>Settings</span>}
          </NavLink>
        </li>
      </ul>

      {/* User profile strip */}
      <div className={styles.userStrip}>
        <button
          className={styles.avatarBtn}
          onClick={handleLogout}
          title="Sign out"
          aria-label="Sign out"
        >
          <span className={styles.avatar} aria-hidden="true">{avatarLetter}</span>
        </button>
        {!collapsed && (
          <div className={styles.userInfo}>
            {user?.displayName && <span className={styles.userDisplayName}>{user.displayName}</span>}
            <span className={styles.userEmail}>{user?.email ?? '—'}</span>
            <button className={styles.signOutBtn} onClick={handleLogout}>
              Sign out
            </button>
          </div>
        )}
      </div>

      {/* Collapse toggle */}
      <button
        className={styles.collapseBtn}
        onClick={onCollapse}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        title={collapsed ? 'Expand' : 'Collapse'}
      >
        <svg
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
          style={{ transform: collapsed ? 'rotate(180deg)' : 'none', transition: 'transform 200ms var(--md-easing-standard)' }}
        >
          <path d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
        </svg>
      </button>
    </nav>
  );
}
