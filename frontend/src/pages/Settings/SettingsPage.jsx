import { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import axiosInstance from '../../api/axiosInstance';
import styles from './SettingsPage.module.css';

export default function SettingsPage() {
  const { user, refreshUser } = useAuth();

  // Profile Form State
  const [defaultCurrency, setDefaultCurrency] = useState(user?.defaultCurrency || 'USD');
  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [theme, setTheme] = useState(localStorage.getItem('theme') || 'light');
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileMessage, setProfileMessage] = useState(null);
  const [copiedUrl, setCopiedUrl] = useState(false);

  const mcpServerUrl = `${window.location.origin}/sse`;

  const handleCopyUrl = () => {
    navigator.clipboard.writeText(mcpServerUrl);
    setCopiedUrl(true);
    setTimeout(() => setCopiedUrl(false), 2000);
  };

  const handleThemeChange = (newTheme) => {
    setTheme(newTheme);
    localStorage.setItem('theme', newTheme);
    document.documentElement.setAttribute('data-theme', newTheme);
  };

  // Sync profile values on load or when user object updates
  useEffect(() => {
    if (user) {
      setDefaultCurrency(user.defaultCurrency || 'USD');
      setDisplayName(user.displayName || '');
    }
  }, [user]);

  // Auto-clear success messages after a few seconds
  useEffect(() => {
    if (profileMessage?.type === 'success') {
      const t = setTimeout(() => setProfileMessage(null), 4000);
      return () => clearTimeout(t);
    }
  }, [profileMessage]);

  // Profile save
  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    setProfileMessage(null);
    try {
      await axiosInstance.patch('/v1/users/me', { defaultCurrency, displayName: displayName.trim() });
      await refreshUser();
      setProfileMessage({ type: 'success', text: 'Profile preferences saved successfully!' });
    } catch (err) {
      setProfileMessage({ type: 'error', text: err.response?.data?.message || err.message || 'Failed to save profile settings.' });
    } finally {
      setSavingProfile(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.headerIcon}>⚙️</span>
        <div className={styles.headerText}>
          <h1>Settings</h1>
          <p>Configure default application preferences and user profile preferences.</p>
        </div>
      </div>

      <div className={styles.contentArea}>
        <div className={styles.profileLayout}>
          <div className={styles.profileHero}>
            <div className={styles.avatar}>
              {displayName ? displayName.charAt(0).toUpperCase() : (user?.email ? user.email.charAt(0).toUpperCase() : 'U')}
            </div>
            <div className={styles.profileInfo}>
              <h2>{displayName || 'Registered User'}</h2>
              <p>{user?.email || 'Resolving profile...'}</p>
            </div>
          </div>

          <div className={styles.card}>
            <form onSubmit={handleSaveProfile} className={styles.categoryForm}>
              <div className={styles.formGroup}>
                <label htmlFor="display-name-input" className={styles.label}>Display Name</label>
                <input
                  id="display-name-input"
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className={styles.input}
                  placeholder="e.g. Alex"
                  required
                />
                <span className={styles.helpText}>
                  Your chosen name used across the platform.
                </span>
              </div>

              <div className={styles.formGroup}>
                <label htmlFor="currency-select" className={styles.label}>Default Currency</label>
                <select
                  id="currency-select"
                  value={defaultCurrency}
                  onChange={(e) => setDefaultCurrency(e.target.value)}
                  className={styles.select}
                >
                  <option value="USD">USD ($) — United States Dollar</option>
                  <option value="EUR">EUR (€) — Euro</option>
                  <option value="RON">RON (lei) — Romanian Leu</option>
                  <option value="GBP">GBP (£) — British Pound</option>
                  <option value="CAD">CAD ($) — Canadian Dollar</option>
                  <option value="CHF">CHF (Fr.) — Swiss Franc</option>
                  <option value="AUD">AUD ($) — Australian Dollar</option>
                  <option value="JPY">JPY (¥) — Japanese Yen</option>
                </select>
                <span className={styles.helpText}>
                  All balances, monthly cash flow charts, and active budgets will automatically convert to and render in this currency.
                </span>
              </div>

              <div className={styles.formGroup}>
                <label htmlFor="theme-select" className={styles.label}>Theme Mode</label>
                <select
                  id="theme-select"
                  value={theme}
                  onChange={(e) => handleThemeChange(e.target.value)}
                  className={styles.select}
                >
                  <option value="light">☀️ Light Mode</option>
                  <option value="dark">🌙 Dark Mode</option>
                </select>
                <span className={styles.helpText}>
                  Choose the visual appearance of the application.
                </span>
              </div>

              {profileMessage && (
                <div
                  className={`${styles.alert} ${profileMessage.type === 'success' ? styles.alertSuccess : styles.alertError}`}
                  role="alert"
                >
                  {profileMessage.text}
                </div>
              )}

              <div className={styles.actions}>
                <button
                  type="submit"
                  disabled={savingProfile || !displayName.trim()}
                  className={`${styles.btn} ${styles.btnPrimary}`}
                >
                  {savingProfile ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>

          <div className={styles.card}>
            <div className={styles.mcpHeader}>
              <span className={styles.mcpBadge}>🤖 MCP Server</span>
              <h2>AI Agent Integration</h2>
              <p>Connect Claude Desktop, Cursor, or external AI agents to Budget Tracker using the Model Context Protocol (MCP).</p>
            </div>

            <div className={styles.mcpDetails}>
              <div>
                <label className={styles.label}>Remote Server Endpoint (SSE)</label>
                <div className={styles.mcpUrlBox}>
                  <span className={styles.mcpUrlText}>{mcpServerUrl}</span>
                  <button
                    type="button"
                    onClick={handleCopyUrl}
                    className={`${styles.btn} ${styles.btnPrimary}`}
                    style={{ padding: '6px 14px', fontSize: '13px' }}
                  >
                    {copiedUrl ? '✓ Copied' : 'Copy URL'}
                  </button>
                </div>
                <span className={styles.helpText}>
                  Supports native OAuth 2.1 authentication with PKCE. When an agent attempts to connect, a browser pop-up will ask you to approve access. No API keys needed!
                </span>
              </div>

              <div className={styles.featureList}>
                <div className={styles.featureItem}>
                  <h3>💸 Transactions (Read/Write)</h3>
                  <p>Agents can list, create, transfer, update, and delete transactions. Account is automatically resolved if not specified.</p>
                </div>
                <div className={styles.featureItem}>
                  <h3>🏷️ Categories (Read/Write)</h3>
                  <p>Inspect existing categories and create custom spending categories on the fly.</p>
                </div>
                <div className={styles.featureItem}>
                  <h3>📊 Analytics (Read-Only)</h3>
                  <p>Summary dashboard, category breakdowns, and net cash flow insights.</p>
                </div>
                <div className={styles.featureItem}>
                  <h3>🎯 Goals (Read-Only)</h3>
                  <p>Query active savings goals, progress toward targets, and goal transactions safely.</p>
                </div>
              </div>

              <div>
                <label className={styles.label}>Example Claude Desktop Config</label>
                <pre className={styles.configPre}>
{JSON.stringify({
  mcpServers: {
    budgetTracker: {
      url: mcpServerUrl
    }
  }
}, null, 2)}
                </pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
