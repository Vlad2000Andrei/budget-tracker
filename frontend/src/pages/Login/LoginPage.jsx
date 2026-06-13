import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import styles from './LoginPage.module.css';

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const btnRef = useRef(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // Wait for the GIS SDK to be available
    const initGoogleSignIn = () => {
      if (!window.google?.accounts?.id) return;

      window.google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: handleCredentialResponse,
        auto_select: false,
        cancel_on_tap_outside: true,
      });

      window.google.accounts.id.renderButton(btnRef.current, {
        type: 'standard',
        shape: 'pill',
        theme: 'outline',
        text: 'signin_with',
        size: 'large',
        logo_alignment: 'left',
        width: 280,
      });
    };

    // SDK might already be loaded or still loading
    if (window.google?.accounts?.id) {
      initGoogleSignIn();
    } else {
      const script = document.querySelector('script[src*="accounts.google.com/gsi/client"]');
      if (script) {
        script.addEventListener('load', initGoogleSignIn);
        return () => script.removeEventListener('load', initGoogleSignIn);
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleCredentialResponse(credentialResponse) {
    setError(null);
    setLoading(true);
    const result = await login(credentialResponse.credential);
    setLoading(false);

    if (result.success) {
      navigate('/', { replace: true });
    } else {
      setError(result.error ?? 'Sign-in failed. Please try again.');
    }
  }

  return (
    <div className={styles.page}>
      {/* Background decorative blobs */}
      <div className={styles.blobA} aria-hidden="true" />
      <div className={styles.blobB} aria-hidden="true" />

      <div className={styles.card} role="main">
        <div className={styles.logoRow}>
          <span className={styles.logoEmoji} aria-hidden="true">💰</span>
          <h1 className={styles.appName}>Budget Tracker</h1>
        </div>

        <p className={styles.tagline}>
          Your personal finance dashboard.<br />
          Multi-currency. Goal-driven. Effortless.
        </p>

        <div className={styles.divider} aria-hidden="true" />

        <p className={styles.signInLabel}>Sign in to get started</p>

        {/* Google Sign-In button rendered by the GIS SDK */}
        <div className={styles.gsiWrapper}>
          <div ref={btnRef} id="google-signin-btn" />
        </div>

        {loading && (
          <div className={styles.loadingRow} aria-live="polite" aria-label="Signing in…">
            <span className={styles.spinner} aria-hidden="true" />
            <span>Signing in…</span>
          </div>
        )}

        {error && (
          <div className={styles.errorBanner} role="alert">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
            </svg>
            {error}
          </div>
        )}

        <p className={styles.disclaimer}>
          We only use your Google account to identify you.<br />
          No passwords stored.
        </p>
      </div>
    </div>
  );
}
