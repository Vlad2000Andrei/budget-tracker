import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import axiosInstance from '../../api/axiosInstance';
import styles from './AccountsPage.module.css';

function formatBalance(amount, currency) {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
      minimumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${currency || 'USD'} ${amount}`;
  }
}

export default function AccountsPage() {
  const { user } = useAuth();
  
  // State variables
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [alert, setAlert] = useState(null);
  const [accountToDelete, setAccountToDelete] = useState(null);
  const [confirmName, setConfirmName] = useState('');
  const [confirmBalance, setConfirmBalance] = useState('');

  // Form input states
  const [name, setName] = useState('');
  const [type, setType] = useState('CHECKING');
  const [currency, setCurrency] = useState(user?.defaultCurrency || 'USD');
  const [editingAccount, setEditingAccount] = useState(null);
  const [initialBalance, setInitialBalance] = useState('');
  const [isFormOpen, setIsFormOpen] = useState(false);

  // Load user default currency when profile updates
  useEffect(() => {
    if (user?.defaultCurrency && !editingAccount) {
      setCurrency(user.defaultCurrency);
    }
  }, [user, editingAccount]);

  // Clear alerts automatically
  useEffect(() => {
    if (alert?.type === 'success') {
      const timer = setTimeout(() => setAlert(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [alert]);

  // Fetch accounts list
  const fetchAccounts = useCallback(async () => {
    try {
      const response = await axiosInstance.get('/v1/accounts');
      setAccounts(response.data);
    } catch (err) {
      setAlert({ type: 'error', text: err.message || 'Failed to fetch accounts.' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  // Handle Form Reset
  const resetForm = () => {
    setEditingAccount(null);
    setName('');
    setType('CHECKING');
    setCurrency(user?.defaultCurrency || 'USD');
    setInitialBalance('');
    setIsFormOpen(false);
  };

  // Switch to Edit mode
  const startEdit = (account) => {
    setEditingAccount(account);
    setName(account.name);
    setType(account.type);
    setCurrency(account.currency);
    setAlert(null);
    setIsFormOpen(true);
  };

  // Submit Create or Update
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) return;

    setSaving(true);
    setAlert(null);
    try {
      if (editingAccount) {
        // Update Account Name
        const payload = { name: name.trim() };
        await axiosInstance.patch(`/v1/accounts/${editingAccount.id}`, payload);
        setAlert({ type: 'success', text: `Account "${editingAccount.name}" renamed to "${payload.name}" successfully.` });
      } else {
        // Create New Account
        const payload = {
          name: name.trim(),
          type,
          currency,
          initialBalance: initialBalance ? parseFloat(initialBalance) : undefined,
        };
        const response = await axiosInstance.post('/v1/accounts', payload);
        setAlert({ type: 'success', text: `Account "${response.data.name}" created successfully.` });
      }
      resetForm();
      fetchAccounts();
      // Notify components to update summaries (balances)
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      setAlert({ type: 'error', text: err.message || 'Failed to save account.' });
    } finally {
      setSaving(false);
    }
  };

  const promptDelete = (account) => {
    setAccountToDelete(account);
    setConfirmName('');
    setConfirmBalance('');
  };

  const confirmDeleteAccount = async () => {
    if (!accountToDelete) return;
    const account = accountToDelete;
    
    setAccountToDelete(null);
    setConfirmName('');
    setConfirmBalance('');
    setAlert(null);
    try {
      await axiosInstance.delete(`/v1/accounts/${account.id}`);
      setAlert({ type: 'success', text: `Account "${account.name}" deleted successfully.` });
      
      // If the deleted account was being edited, clear form editing state
      if (editingAccount?.id === account.id) {
        resetForm();
      }
      
      fetchAccounts();
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      setAlert({ type: 'error', text: err.message || 'Failed to delete account.' });
    }
  };

  return (
    <div className={styles.container}>
      {/* Header */}
      <header className={styles.header}>
        <span className={styles.headerIcon} role="img" aria-label="Bank building">🏦</span>
        <div className={styles.headerText}>
          <h1>Accounts</h1>
          <p>Configure checking and savings accounts and track your current balances.</p>
        </div>
        <button
          className={`${styles.btn} ${styles.btnPrimary} ${styles.mobileAddBtn}`}
          onClick={() => setIsFormOpen(true)}
          aria-label="Add Account"
          title="Add Account"
        >
          <span className={styles.mobileAddBtnIcon} aria-hidden="true">+</span>
          <span className={styles.mobileAddBtnLabel}>Add Account</span>
        </button>
      </header>

      {/* Main Layout Grid */}
      <div className={styles.layout}>
        {/* Left Column: Account cards list */}
        <main className={styles.mainContent}>
          {loading ? (
            <div className={styles.emptyState}>
              <div className="spinner" aria-hidden="true" />
              <p>Loading accounts...</p>
            </div>
          ) : accounts.length === 0 ? (
            <div className={styles.emptyState}>
              <span className={styles.emptyIcon} role="img" aria-label="Inbox empty">📭</span>
              <p>No accounts configured yet. Use the form to add your first checking or savings account.</p>
            </div>
          ) : (
            <div className={styles.accountsList}>
              {accounts.map((account) => {
                const isChecking = account.type === 'CHECKING';
                return (
                  <div
                    key={account.id}
                    className={`${styles.accountCard} ${isChecking ? styles.checkingAccent : styles.savingsAccent}`}
                  >
                    <div className={styles.cardHeader}>
                      <span className={styles.accountName} title={account.name}>{account.name}</span>
                      <span
                        className={`${styles.badge} ${isChecking ? styles.checkingBadge : styles.savingsBadge}`}
                      >
                        {isChecking ? 'Checking' : 'Savings'}
                      </span>
                    </div>

                    <div className={styles.balance}>
                      {formatBalance(account.balance, account.currency)}
                    </div>

                    <div className={styles.cardFooter}>
                      <span className={styles.currencyLabel}>{account.currency}</span>
                      <div className={styles.actions}>
                        <button
                          className={styles.actionBtn}
                          onClick={() => startEdit(account)}
                          title="Edit Name"
                          aria-label={`Edit ${account.name} name`}
                        >
                          <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                          </svg>
                        </button>
                        <button
                          className={`${styles.actionBtn} ${styles.actionBtnDanger}`}
                          onClick={() => promptDelete(account)}
                          title="Delete Account"
                          aria-label={`Delete ${account.name}`}
                        >
                          <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
                            <path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                          </svg>
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </main>

        {/* Right Column: Context form */}
        <aside className={`${styles.sidebarContainer} ${isFormOpen ? styles.isOpen : ''}`}>
          <div className={styles.backdrop} onClick={resetForm} />
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            {/* Drag handle */}
            <div className={styles.mobileHandle} aria-hidden="true" />
            <div className={styles.formHeader}>
              <h2>{editingAccount ? 'Edit Account' : 'Add Account'}</h2>
              <button
                type="button"
                className={styles.closeBtn}
                onClick={resetForm}
                aria-label="Close form"
              >
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleSubmit} className={styles.form}>
              {/* Alert status */}
              {alert && (
                <div
                  className={`${styles.alert} ${alert.type === 'success' ? styles.alertSuccess : styles.alertError}`}
                  role="alert"
                >
                  <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
                    {alert.type === 'success' ? (
                      <path d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                    ) : (
                      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                    )}
                  </svg>
                  <span>{alert.text}</span>
                </div>
              )}

              {/* Name */}
              <div className={styles.formGroup}>
                <label htmlFor="account-name" className={styles.label}>Account Name</label>
                <input
                  id="account-name"
                  type="text"
                  placeholder="e.g. Primary Checking"
                  maxLength={100}
                  className={styles.input}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </div>

              {/* Initial Balance (Create mode only) */}
              {!editingAccount && (
                <div className={styles.formGroup}>
                  <label htmlFor="account-initial-balance" className={styles.label}>Initial Balance</label>
                  <input
                    id="account-initial-balance"
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="0.00"
                    className={styles.input}
                    value={initialBalance}
                    onChange={(e) => setInitialBalance(e.target.value)}
                  />
                </div>
              )}

              {/* Type (Create mode only) */}
              <div className={styles.formGroup}>
                <label htmlFor="account-type" className={styles.label}>Account Type</label>
                <select
                  id="account-type"
                  className={styles.select}
                  value={type}
                  onChange={(e) => setType(e.target.value)}
                  disabled={!!editingAccount}
                >
                  <option value="CHECKING">Checking Account</option>
                  <option value="SAVINGS">Savings Account</option>
                </select>
                {editingAccount && (
                  <span className={styles.helpText}>Account type cannot be modified after creation.</span>
                )}
              </div>

              {/* Currency (Create mode only) */}
              <div className={styles.formGroup}>
                <label htmlFor="account-currency" className={styles.label}>Currency</label>
                <select
                  id="account-currency"
                  className={styles.select}
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                  disabled={!!editingAccount}
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
                {editingAccount && (
                  <span className={styles.helpText}>Account currency cannot be modified after creation.</span>
                )}
              </div>

              {/* Form Actions (Cancel + Save) */}
              <div className={styles.formActions}>
                <button
                  type="button"
                  className={`${styles.btn} ${styles.btnOutlinedDanger}`}
                  onClick={resetForm}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saving || !name.trim()}
                  className={`${styles.btn} ${styles.btnPrimary}`}
                >
                  {saving ? 'Saving...' : editingAccount ? 'Save Changes' : 'Create Account'}
                </button>
              </div>
            </form>
          </div>
        </aside>
      </div>

      {/* ── Custom Deletion Confirmation Bottom-Sheet Modal ── */}
      {accountToDelete && (
        <div
          className={styles.confirmBackdrop}
          onClick={(e) => { if (e.target === e.currentTarget) setAccountToDelete(null); }}
          role="dialog"
          aria-modal="true"
          aria-label="Confirm account deletion"
        >
          <div className={styles.confirmToast}>
            <div className={styles.confirmHeader}>
              <h3 className={styles.confirmTitle}>Delete Account</h3>
              <button
                className={styles.cancelEditBtn}
                onClick={() => setAccountToDelete(null)}
                aria-label="Cancel"
                style={{ fontSize: '16px' }}
              >
                ✕
              </button>
            </div>
            
            <div className={styles.confirmBody}>
              <div className={styles.confirmWarning}>
                ⚠️ Warning: Deleting the account &quot;{accountToDelete.name}&quot; is a destructive action and cannot be undone. All linked transactions will lose their association.
              </div>

              <div className={styles.confirmForm}>
                <div className={styles.formGroup}>
                  <label htmlFor="confirm-account-name" className={styles.label}>
                    To confirm, type the account name: <strong>{accountToDelete.name}</strong>
                  </label>
                  <input
                    id="confirm-account-name"
                    type="text"
                    className={styles.input}
                    placeholder="Type account name..."
                    value={confirmName}
                    onChange={(e) => setConfirmName(e.target.value)}
                    required
                  />
                </div>

                <div className={styles.formGroup}>
                  <label htmlFor="confirm-account-balance" className={styles.label}>
                    Type the current balance: <strong>{parseFloat(accountToDelete.balance).toFixed(2)}</strong>
                  </label>
                  <input
                    id="confirm-account-balance"
                    type="number"
                    step="0.01"
                    className={styles.input}
                    placeholder="Type balance..."
                    value={confirmBalance}
                    onChange={(e) => setConfirmBalance(e.target.value)}
                    required
                  />
                </div>
              </div>
            </div>

            <div className={styles.confirmActions}>
              <button
                className={styles.cancelEditBtn}
                onClick={() => setAccountToDelete(null)}
                style={{ padding: '8px 16px', fontSize: '14px' }}
              >
                Cancel
              </button>
              <button
                className={`${styles.btn} ${styles.btnDanger}`}
                onClick={confirmDeleteAccount}
                disabled={
                  confirmName !== accountToDelete.name ||
                  isNaN(parseFloat(confirmBalance)) ||
                  parseFloat(confirmBalance) !== parseFloat(accountToDelete.balance)
                }
              >
                Verify & Delete Account
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
