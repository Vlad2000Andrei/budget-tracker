import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import axiosInstance from '../../api/axiosInstance';
import { getCategoryIcon } from '../../api/utils';
import styles from './WelcomePage.module.css';

export default function WelcomePage() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  
  // Onboarding Step state: 1 = Display Name, 2 = Currency, 3 = Categories Showcase, 4 = Accounts Setup
  const [step, setStep] = useState(1);
  const [defaultCurrency, setDefaultCurrency] = useState('USD');
  const [displayName, setDisplayName] = useState('');
  const [categories, setCategories] = useState([]);
  
  // Local temporary array representing accounts user added in Step 3
  const [localAccounts, setLocalAccounts] = useState([]);
  
  // In-line single account form states (Step 3)
  const [tempAccountName, setTempAccountName] = useState('Primary Checking');
  const [tempAccountType, setTempAccountType] = useState('CHECKING');
  const [tempAccountBalance, setTempAccountBalance] = useState('0.00');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Set default display name from user email on first load
  useEffect(() => {
    if (user && !displayName) {
      const emailPrefix = user.email ? user.email.split('@')[0] : '';
      const defaultName = emailPrefix ? emailPrefix.charAt(0).toUpperCase() + emailPrefix.slice(1) : '';
      setDisplayName(user.displayName || defaultName);
    }
  }, [user]);

  // Load categories on mount to display in Step 2
  useEffect(() => {
    async function fetchCategories() {
      try {
        const response = await axiosInstance.get('/v1/categories');
        setCategories(response.data);
      } catch (err) {
        console.error('Failed to load categories in onboarding wizard', err);
      }
    }
    fetchCategories();
  }, []);

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

      // 2. Patch user default currency and display name. The backend automatically marks the user as onboarded.
      await axiosInstance.patch('/v1/users/me', { defaultCurrency, displayName: displayName.trim() });
      
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
      <div className={styles.blobC} aria-hidden="true" />

      <div className={styles.card} role="main">
        {step === 1 && (
          /* ──────────────── STEP 1: DISPLAY NAME ──────────────── */
          <div style={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <span className={styles.welcomeIcon} role="img" aria-label="Waving hand and party popper">👋✨🎉</span>
            <h1 className={styles.title}>Welcome!</h1>
            <p className={styles.subtitle}>
              We are so excited to have you here. What should we call you?
            </p>

            <div className={styles.divider} aria-hidden="true" />

            <div className={styles.form}>
              <div className={styles.formGroup}>
                <label htmlFor="welcome-display-name" className={styles.label}>Your Display Name</label>
                <input
                  id="welcome-display-name"
                  type="text"
                  className={styles.input}
                  placeholder="e.g. Alex"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  required
                />
              </div>

              <div className={styles.onboardingNote}>
                💡 <strong>Tip:</strong> Don't worry, you can always change your display name later in your Account Settings.
              </div>

              <button
                type="button"
                onClick={() => setStep(2)}
                disabled={!displayName.trim()}
                className={styles.btn}
                style={{ marginTop: '16px' }}
              >
                Continue
              </button>
            </div>
          </div>
        )}

        {step === 2 && (
          /* ──────────────── STEP 2: CURRENCY CHOICE ──────────────── */
          <div style={{ width: '100%' }}>
            <h1 className={styles.title}>Select Currency 💳</h1>
            <p className={styles.subtitle}>
              Let's set your default base currency. This is the currency we will use for dashboard summaries, budgets, and savings goals.
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
                  All transaction balances in other currencies will automatically convert to this base currency.
                </span>
              </div>

              <div style={{ display: 'flex', gap: '12px', width: '100%', marginTop: '16px' }}>
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className={`${styles.btn} ${styles.btnBack}`}
                  style={{ flex: 1 }}
                >
                  Back
                </button>
                <button
                  type="button"
                  onClick={() => setStep(3)}
                  className={styles.btn}
                  style={{ flex: 2 }}
                >
                  Continue to Categories
                </button>
              </div>
            </div>
          </div>
        )}

        {step === 3 && (
          /* ──────────────── STEP 3: CATEGORIES SHOWCASE ──────────────── */
          <div style={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
            <h1 className={styles.title}>Categories</h1>
            <p className={styles.subtitle}>
              We have pre-seeded several starting defaults for you, but we highly encourage you to create your own custom categories to match your unique spending habits!
            </p>

            <div className={styles.divider} aria-hidden="true" />

            {/* Showcase Parent Categories */}
            <div className={styles.welcomeCategoriesList}>
              {categories.length === 0 ? (
                <span className={styles.helpText}>Seeding default categories...</span>
              ) : (
                categories
                  .filter(c => c.parentId === null)
                  .map(c => (
                    <span key={c.id} className={styles.welcomeCategoryChip}>
                      {getCategoryIcon(c.icon)} {c.name}
                    </span>
                  ))
              )}
            </div>

            <div className={styles.onboardingNote}>
              💡 <strong>Where to find them:</strong> You can manage, rename, or add custom categories at any time in the new <strong>Categories</strong> section of the main navigation menu.
            </div>

            <div style={{ display: 'flex', gap: '12px', width: '100%', marginTop: '8px' }}>
              <button
                type="button"
                onClick={() => setStep(2)}
                className={`${styles.btn} ${styles.btnBack}`}
                style={{ flex: 1 }}
              >
                Back
              </button>
              <button
                type="button"
                onClick={() => setStep(4)}
                className={styles.btn}
                style={{ flex: 2 }}
              >
                Continue to Accounts Setup
              </button>
            </div>
          </div>
        )}

        {step === 4 && (
          /* ──────────────── STEP 4: ACCOUNTS SETUP ──────────────── */
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
                className={styles.btn}
                style={{ width: 'auto', alignSelf: 'flex-start', marginTop: 0 }}
              >
                Save
              </button>
            </div>

            <div className={styles.divider} aria-hidden="true" style={{ margin: '24px 0' }} />

            {/* Explanatory Reminder Note */}
            <div className={styles.onboardingNote}>
              💡 <strong>Tip:</strong> You can always manage, edit account names, or delete accounts later by visiting the <strong>Accounts</strong> tab in the navigation.
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
                onClick={() => setStep(3)}
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
