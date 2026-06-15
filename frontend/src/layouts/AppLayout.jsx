import { useState, useEffect, useRef } from 'react';
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Sidebar from '../components/Sidebar/Sidebar';
import FAB from '../components/FAB/FAB';
import AddTransactionModal from '../components/AddTransactionModal/AddTransactionModal';
import styles from './AppLayout.module.css';

const MOBILE_NAV_ITEMS = [
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
        <path d="M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z"/>
      </svg>
    ),
    label: 'Stats',
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
        <path d="M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2-.9-2 2v8c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z" />
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

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const dropdownRef = useRef(null);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setProfileOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const avatarLetter = user?.displayName
    ? user.displayName[0].toUpperCase()
    : user?.email
    ? user.email[0].toUpperCase()
    : '?';

  return (
    <div className={styles.shell}>
      <Sidebar
        collapsed={collapsed}
        onCollapse={() => setCollapsed((c) => !c)}
      />

      <div className={styles.contentContainer}>
        {/* Mobile Top App Bar */}
        <header className={styles.mobileTopBar} aria-label="Application header">
          <div className={styles.logo}>
            <span className={styles.logoIcon}>💰</span>
            <span className={styles.logoText}>Budget Tracker</span>
          </div>

          <div className={styles.profileArea} ref={dropdownRef}>
            <button
              className={styles.avatarBtn}
              onClick={() => setProfileOpen((prev) => !prev)}
              aria-label="Toggle profile menu"
              aria-expanded={profileOpen}
            >
              <span className={styles.avatar}>{avatarLetter}</span>
            </button>

            {profileOpen && (
              <div className={styles.profileDropdown} role="menu">
                <div className={styles.dropdownHeader}>
                  {user?.displayName && <div className={styles.userName}>{user.displayName}</div>}
                  <span className={styles.userEmail}>{user?.email ?? '—'}</span>
                </div>
                <div className={styles.divider} />
                <button
                  className={styles.settingsBtn}
                  onClick={() => {
                    setProfileOpen(false);
                    navigate('/settings');
                  }}
                  role="menuitem"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" style={{ marginRight: '8px' }}>
                    <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7 7 0 0 0-1.62-.94l-.36-2.54A.484.484 0 0 0 14 2h-4a.484.484 0 0 0-.48.41l-.36 2.54a7.36 7.36 0 0 0-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87a.47.47 0 0 0 .12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.36 1.04.67 1.62.94l.36 2.54c.05.24.27.41.49.41h4c.22 0 .44-.17.47-.41l.36-2.54a7.36 7.36 0 0 0 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.47.47 0 0 0-.12-.61l-2.01-1.58zM12 15.6a3.6 3.6 0 1 1 0-7.2 3.6 3.6 0 0 1 0 7.2z" />
                  </svg>
                  Settings
                </button>
                <button
                  className={styles.signOutBtn}
                  onClick={handleLogout}
                  role="menuitem"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" style={{ marginRight: '8px' }}>
                    <path d="m17 7-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"/>
                  </svg>
                  Sign out
                </button>
              </div>
            )}
          </div>
        </header>

        <main className={styles.main}>
          <Outlet />
        </main>

        {pathname !== '/accounts' && pathname !== '/categories' && pathname !== '/goals' && (
          <FAB onClick={() => setModalOpen(true)} />
        )}

        {/* Mobile Bottom Navigation Bar */}
        <nav className={styles.mobileNavBar} aria-label="Mobile navigation">
          {MOBILE_NAV_ITEMS.map(({ to, end, icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `${styles.mobileNavItem} ${isActive ? styles.mobileActive : ''}`
              }
            >
              <div className={styles.iconContainer}>
                {icon}
              </div>
              <span className={styles.mobileLabel}>{label}</span>
            </NavLink>
          ))}
        </nav>
      </div>

      {modalOpen && (
        <AddTransactionModal onClose={() => setModalOpen(false)} />
      )}
    </div>
  );
}
