import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import axiosInstance from '../../api/axiosInstance';
import styles from './WelcomePage.module.css';

export default function WelcomePage() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  
  // Onboarding Step state: 1 = Currency, 2 = Accounts Setup
  const [step, setStep] = useState(1);
  const [defaultCurrency, setDefaultCurrency] = useState('USD');
  
  // Local temporary array representing accounts user added in Step 2
  const [localAccounts, setLocalAccounts] = useState([]);
  
  // In-line single account form states (Step 2)
  const [tempAccountName, setTempAccountName] = useState('Primary Checking');
  const [tempAccountType, setTempAccountType] = useState('CHECKING');
  const [tempAccountBalance, setTempAccountBalance] = useState('0.00');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Add account definition to local array
  const addLocalAccount = () => {
    if (!tempAccountName.trim()) return;
    
    const newAccount = {
      name: tempAccountName.trim(),
      type: tempAccountType,
      initialBalance: tempAccountBalance ? parseFloat(tempAccountBalance) : 0,
    };
    
    setLocalAccounts((prev) => [...prev, newAccount]);
    
    // Reset inputs for next entry, toggling default suggestions
    if (tempAccountName.trim() === 'Primary Checking') {
      setTempAccountName('Savings Account');
      setTempAccountType('SAVINGS');
    } else {
      setTempAccountName('');
    }
    setTempAccountBalance('0.00');
  };

  // Remove local account by index
  const removeLocalAccount = (index) => {
    setLocalAccounts((prev) => prev.filter((_, i) => i !== index));
  };

  // Submit and finalize onboarding wizard
  const completeOnboarding = async () => {
    if (localAccounts.length === 0) {
      setError('Please add at least one account to get started.');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      // 1. Create all locally added accounts in sequence
      for (const account of localAccounts) {
        await axiosInstance.post('/v1/accounts', {
          name: account.name,
          type: account.type,
          currency: defaultCurrency,
          initialBalance: account.initialBalance,
        });
      }

      // 2. Patch user default currency. The backend automatically marks the user as onboarded.
      await axiosInstance.patch('/v1/users/me', { defaultCurrency });
      
      // 3. Refresh user context globally
      await refreshUser();
      
      // 4. Redirect to main application dashboard
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to complete setup. Please try again.');
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
        {step === 1 ? (
          /* ──────────────── STEP 1: CURRENCY CHOICE ──────────────── */
          <div>
            <span className={styles.welcomeIcon} role="img" aria-label="Waving hand and coins">👋✨</span>
            <h1 className={styles.title}>Welcome!</h1>
            <p className={styles.subtitle}>
              {"Let's"} set up your base preferences for {user?.email || 'your account'} to get started.
            </p>

            <div className={styles.divider} aria-hidden="true" />

            <div className={styles.form}>
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
                  This sets the primary currency for your dashboard summaries, budget checking, and savings goals.
                </span>
              </div>

              <button
                type="button"
                onClick={() => setStep(2)}
                className={styles.btn}
                style={{ marginTop: '24px' }}
              >
                Continue to Accounts Setup
              </button>
            </div>
          </div>
        ) : (
          /* ──────────────── STEP 2: MULTIPLE ACCOUNTS ──────────────── */
          <div style={{ width: '100%' }}>
            <h1 className={styles.title}>Setup Accounts</h1>
            <p className={styles.subtitle}>
              Add at least one checking or savings account to get started.
            </p>

            <div className={styles.divider} aria-hidden="true" />

            {/* List of currently added accounts */}
            {localAccounts.length > 0 && (
              <div className={styles.localAccountsList} role="list">
                {localAccounts.map((acc, index) => (
                  <div key={index} className={styles.localAccountRow} role="listitem">
                    <div className={styles.localAccountInfo}>
                      <span className={styles.localAccountIcon} aria-hidden="true">
                        {acc.type === 'CHECKING' ? '💳' : '💰'}
                      </span>
                      <div>
                        <div className={styles.localAccountName}>{acc.name}</div>
                        <div className={styles.localAccountMeta}>
                          {acc.type === 'CHECKING' ? 'Checking' : 'Savings'} • {
                            new Intl.NumberFormat('en-US', {
                              style: 'currency',
                              currency: defaultCurrency,
                            }).format(acc.initialBalance)
                          }
                        </div>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => removeLocalAccount(index)}
                      className={styles.removeBtn}
                      title="Remove Account"
                      aria-label={`Remove ${acc.name}`}
                    >
                      ✕
                    </button>
                  </div>
                ))}
                <div className={styles.divider} aria-hidden="true" style={{ margin: '16px 0' }} />
              </div>
            )}

            {/* Inline Account addition form */}
            <div className={styles.inlineForm}>
              <h2 style={{ fontSize: 'var(--md-title-small-size)', margin: '0 0 12px 0', color: 'var(--md-on-surface)' }}>
                Add an Account
              </h2>
              <div className={styles.formGroup} style={{ marginBottom: '12px' }}>
                <label htmlFor="temp-name" className={styles.label}>Account Name</label>
                <input
                  id="temp-name"
                  type="text"
                  className={styles.input}
                  placeholder="e.g. Primary Checking"
                  value={tempAccountName}
                  onChange={(e) => setTempAccountName(e.target.value)}
                />
              </div>
              <div className={styles.formGroup} style={{ marginBottom: '12px' }}>
                <label htmlFor="temp-type" className={styles.label}>Account Type</label>
                <select
                  id="temp-type"
                  className={styles.select}
                  value={tempAccountType}
                  onChange={(e) => setTempAccountType(e.target.value)}
                >
                  <option value="CHECKING">Checking Account</option>
                  <option value="SAVINGS">Savings Account</option>
                </select>
              </div>
              <div className={styles.formGroup} style={{ marginBottom: '16px' }}>
                <label htmlFor="temp-balance" className={styles.label}>Initial Balance ({defaultCurrency})</label>
                <input
                  id="temp-balance"
                  type="number"
                  step="0.01"
                  className={styles.input}
                  placeholder="0.00"
                  value={tempAccountBalance}
                  onChange={(e) => setTempAccountBalance(e.target.value)}
                />
              </div>
              <button
                type="button"
                onClick={addLocalAccount}
                disabled={!tempAccountName.trim()}
                className={`${styles.btn} ${styles.btnSecondary}`}
                style={{ width: 'auto', alignSelf: 'flex-start' }}
              >
                + Add to List
              </button>
            </div>

            <div className={styles.divider} aria-hidden="true" style={{ margin: '24px 0' }} />

            {/* Explanatory Reminder Note */}
            <div className={styles.onboardingNote}>
              💡 <strong>Tip:</strong> You can always manage, edit account names, or delete accounts later by visiting the <strong>Accounts</strong> tab in the sidebar.
            </div>

            {error && (
              <div className={styles.errorBanner} role="alert" style={{ marginBottom: '16px' }}>
                <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                </svg>
                <span>{error}</span>
              </div>
            )}

            {/* Navigation / Submission */}
            <div style={{ display: 'flex', gap: '12px', width: '100%' }}>
              <button
                type="button"
                onClick={() => setStep(1)}
                disabled={loading}
                className={`${styles.btn} ${styles.btnBack}`}
                style={{ flex: 1 }}
              >
                Back
              </button>
              <button
                type="button"
                onClick={completeOnboarding}
                disabled={loading || localAccounts.length === 0}
                className={styles.btn}
                style={{ flex: 2 }}
              >
                {loading ? 'Completing Setup...' : 'Complete Setup & Get Started'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
