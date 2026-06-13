import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import axiosInstance from '../../api/axiosInstance';
import styles from './WelcomePage.module.css';

export default function WelcomePage() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [defaultCurrency, setDefaultCurrency] = useState('USD');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      // Patch user default currency. The backend automatically marks user as onboarded.
      await axiosInstance.patch('/v1/users/me', { defaultCurrency });
      
      // Refresh context user profile so the onboarding routing guard accepts the transition
      await refreshUser();
      
      // Navigate to dashboard home
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message || 'Failed to complete setup. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.page}>
      {/* Decorative background blobs */}
      <div className={styles.blobA} aria-hidden="true" />
      <div className={styles.blobB} aria-hidden="true" />

      <div className={styles.card} role="main">
        <span className={styles.welcomeIcon} role="img" aria-label="Waving hand and coins">👋✨</span>
        <h1 className={styles.title}>Welcome!</h1>
        <p className={styles.subtitle}>
          {"Let's"} set up your profile for {user?.email || 'your account'} to get started.
        </p>

        <div className={styles.divider} aria-hidden="true" />

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.formGroup}>
            <label htmlFor="welcome-currency" className={styles.label}>Choose your Default Currency</label>
            <select
              id="welcome-currency"
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
              This sets the primary currency for your dashboard summary cards, budget progress monitoring, and default transaction entries. You can update this preference anytime in Settings.
            </span>
          </div>

          {error && (
            <div className={styles.errorBanner} role="alert">
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
              </svg>
              {error}
            </div>
          )}

          <button type="submit" disabled={loading} className={styles.btn}>
            {loading ? 'Completing Setup...' : 'Complete Setup & Get Started'}
          </button>
        </form>
      </div>
    </div>
  );
}
