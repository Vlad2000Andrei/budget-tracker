import { useState, useEffect, useCallback, useRef } from 'react';
import axiosInstance from '../../api/axiosInstance';
import { getCategoryIcon } from '../../api/utils';
import styles from './TransactionLog.module.css';

const TYPE_FILTER_OPTIONS = ['All', 'INCOME', 'EXPENSE', 'SAVINGS'];

function fmt(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(Math.abs(amount));
}

function TypeBadge({ type }) {
  return (
    <span className={`${styles.typeBadge} ${styles[`type_${type}`]}`}>
      {type === 'INCOME' ? 'Income' : type === 'EXPENSE' ? 'Expense' : 'Savings'}
    </span>
  );
}

function RecurringIcon({ size = 13 }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="currentColor"
      width={size}
      height={size}
      className={styles.recurIcon}
      aria-label="Recurring"
      role="img"
    >
      <path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 0 0 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/>
    </svg>
  );
}

/* ── Transaction Detail Toast ──────────────────────────────────── */
function TransactionToast({ transaction: t, categoriesMap, accountsMap, onClose, onDeleted }) {
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const backdropRef = useRef(null);

  const category = categoriesMap[t.categoryId] || { name: 'Unknown', icon: '📦' };
  const accountName = t.accountId ? accountsMap[t.accountId] : null;
  const isIncome = t.type === 'INCOME';
  const isSavings = t.type === 'SAVINGS';
  const isPositiveSavings = isSavings && t.amount > 0;

  const isPositive = isIncome || isPositiveSavings;

  // Close on Escape
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  async function handleDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await axiosInstance.delete(`/v1/transactions/${t.id}`);
      window.dispatchEvent(new Event('transaction-added'));
      onDeleted();
      onClose();
    } catch {
      setDeleteError('Failed to delete. Please try again.');
      setDeleting(false);
    }
  }

  return (
    <div
      className={styles.toastBackdrop}
      ref={backdropRef}
      onClick={(e) => { if (e.target === backdropRef.current) onClose(); }}
      role="dialog"
      aria-modal="true"
      aria-label="Transaction details"
    >
      <div className={styles.toast}>
        <div className={styles.toastHandle} aria-hidden="true" />

        {/* Header */}
        <div className={styles.toastHeader}>
          <div className={styles.toastHeaderLeft}>
            <span className={styles.toastIcon} aria-hidden="true">{category.icon}</span>
            <div>
              <div className={styles.toastCategory}>
                {category.name}
                {t.recurrenceRuleId && (
                  <span className={styles.recurringBadge}>
                    <RecurringIcon />
                  </span>
                )}
              </div>
              <TypeBadge type={t.type} />
            </div>
          </div>
          <button className={styles.toastCloseBtn} onClick={onClose} aria-label="Close">
            <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
              <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
            </svg>
          </button>
        </div>

        {/* Amount */}
        <div className={`${styles.toastAmount} ${isPositive ? styles.positive : styles.negative}`}>
          {isPositive ? '+' : '−'}{fmt(t.amount, t.currency)}
        </div>

        {/* Details grid */}
        <div className={styles.toastDetails}>
          <div className={styles.toastDetailRow}>
            <span className={styles.toastDetailLabel}>Date</span>
            <span className={styles.toastDetailValue}>{t.date.split('T')[0]}</span>
          </div>
          {accountName && (
            <div className={styles.toastDetailRow}>
              <span className={styles.toastDetailLabel}>Account</span>
              <span className={styles.toastDetailValue}>{accountName}</span>
            </div>
          )}
          {t.currency && (
            <div className={styles.toastDetailRow}>
              <span className={styles.toastDetailLabel}>Currency</span>
              <span className={styles.toastDetailValue}>{t.currency}</span>
            </div>
          )}
          {t.recurrenceRuleId && (
            <div className={styles.toastDetailRow}>
              <span className={styles.toastDetailLabel}>Recurring</span>
              <span className={styles.toastDetailValue} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>Yes <RecurringIcon size={14} /></span>
            </div>
          )}
          {t.notes && (
            <div className={styles.toastDetailRow}>
              <span className={styles.toastDetailLabel}>Notes</span>
              <span className={styles.toastDetailValue}>{t.notes}</span>
            </div>
          )}
        </div>

        {deleteError && (
          <div className={styles.toastError} role="alert">{deleteError}</div>
        )}

        {/* Actions */}
        <div className={styles.toastActions}>
          <button className={styles.toastCancelBtn} onClick={onClose} disabled={deleting}>
            Close
          </button>
          <button
            className={styles.toastDeleteBtn}
            onClick={handleDelete}
            disabled={deleting}
            aria-busy={deleting}
          >
            {deleting ? (
              <><div className={styles.spinnerSmall} aria-hidden="true" /> Deleting…</>
            ) : (
              <>
                <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                  <path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                </svg>
                Delete Transaction
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── All Transactions Tab ──────────────────────────────────────── */
function AllTab({ categoriesMap, accountsMap }) {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState('All');
  const [search, setSearch] = useState('');
  const [selectedTx, setSelectedTx] = useState(null);

  const loadTransactions = useCallback(async () => {
    try {
      const res = await axiosInstance.get('/v1/transactions');
      setTransactions(res.data);
    } catch (err) {
      console.error('Failed to load transactions', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTransactions();
    const handleRefresh = () => loadTransactions();
    window.addEventListener('transaction-added', handleRefresh);
    return () => {
      window.removeEventListener('transaction-added', handleRefresh);
    };
  }, [loadTransactions]);

  if (loading) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.spinner} aria-hidden="true" />
        <span>Loading transactions…</span>
      </div>
    );
  }

  const filtered = transactions.filter((t) => {
    if (typeFilter !== 'All' && t.type !== typeFilter) return false;
    const catName = categoriesMap[t.categoryId]?.name || '';
    if (search && !catName.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  return (
    <>
      <div>
        {/* Filter bar */}
        <div className={styles.filterBar}>
          <div className={styles.searchWrap}>
            <svg className={styles.searchIcon} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M15.5 14h-.79l-.28-.27A6.5 6.5 0 1 0 8.5 15a6.5 6.5 0 0 0 4.23-1.57l.27.28v.79l5 4.99L19.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
            </svg>
            <input
              type="search"
              placeholder="Search categories…"
              className={styles.searchInput}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Search transactions"
            />
          </div>
          <div className={styles.chips} role="group" aria-label="Filter by type">
            {TYPE_FILTER_OPTIONS.map((opt) => (
              <button
                key={opt}
                className={`${styles.chip} ${typeFilter === opt ? styles.chipActive : ''}`}
                onClick={() => setTypeFilter(opt)}
                aria-pressed={typeFilter === opt}
              >
                {opt}
              </button>
            ))}
          </div>
        </div>

        {/* Rows */}
        <div className={styles.list} role="list">
          {filtered.length === 0 && (
            <div className={styles.empty}>No transactions found.</div>
          )}
          {filtered.map((t) => {
            const category = categoriesMap[t.categoryId] || { name: 'Unknown', icon: '📦' };
            const accountName = t.accountId ? accountsMap[t.accountId] : null;
            const isIncome = t.type === 'INCOME';
            const isSavings = t.type === 'SAVINGS';
            const isPositiveSavings = isSavings && t.amount > 0;

            const isPositive = isIncome || isPositiveSavings;

            return (
              <button
                key={t.id}
                className={styles.rowBtn}
                onClick={() => setSelectedTx(t)}
                aria-label={`View details for ${category.name} transaction`}
              >
                <span className={styles.rowIcon} aria-hidden="true">{category.icon}</span>
                <div className={styles.rowMain}>
                  <div className={styles.rowTop}>
                    <span className={styles.rowCategory}>
                      {category.name}
                      {t.recurrenceRuleId && (
                        <span className={styles.recurringBadge}>
                          <RecurringIcon />
                        </span>
                      )}
                    </span>
                    <span className={`${styles.rowAmount} ${isPositive ? styles.positive : styles.negative}`}>
                      {isPositive ? '+' : '−'}{fmt(t.amount, t.currency)}
                    </span>
                  </div>
                  <div className={styles.rowBottom}>
                    <TypeBadge type={t.type} />
                    {accountName && <span className={styles.rowMeta}>{accountName}</span>}
                    <span className={styles.rowDate}>{t.date.split('T')[0]}</span>
                    {t.notes && <span className={styles.rowNotes} title={t.notes}>📝</span>}
                  </div>
                </div>
                <svg className={styles.rowChevron} viewBox="0 0 24 24" fill="currentColor" width="16" height="16" aria-hidden="true">
                  <path d="M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>
                </svg>
              </button>
            );
          })}
        </div>
      </div>

      {selectedTx && (
        <TransactionToast
          transaction={selectedTx}
          categoriesMap={categoriesMap}
          accountsMap={accountsMap}
          onClose={() => setSelectedTx(null)}
          onDeleted={loadTransactions}
        />
      )}
    </>
  );
}

/* ── Recurring Tab ─────────────────────────────────────────────── */
function RecurringTab() {
  const [recurring, setRecurring] = useState([]);
  const [loading, setLoading] = useState(true);
  const FREQ_LABEL = { DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly' };

  const loadRecurring = useCallback(async () => {
    try {
      const res = await axiosInstance.get('/v1/recurrence-rules');
      setRecurring(res.data);
    } catch (err) {
      console.error('Failed to load recurrence rules', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRecurring();
    const handleRefresh = () => loadRecurring();
    window.addEventListener('transaction-added', handleRefresh);
    return () => {
      window.removeEventListener('transaction-added', handleRefresh);
    };
  }, [loadRecurring]);

  async function handleDelete(ruleId, categoryName) {
    if (!window.confirm(`Delete the recurring rule for "${categoryName}"? Past transactions will remain, but no new ones will be generated.`)) {
      return;
    }
    try {
      await axiosInstance.delete(`/v1/recurrence-rules/${ruleId}`);
      loadRecurring();
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      alert('Failed to delete recurrence rule: ' + err.message);
    }
  }

  if (loading) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.spinner} aria-hidden="true" />
        <span>Loading recurring transactions…</span>
      </div>
    );
  }

  if (recurring.length === 0) {
    return <div className={styles.empty}>No recurring transactions found.</div>;
  }

  return (
    <div className={styles.list} role="list">
      {recurring.map((r) => {
        const icon = getCategoryIcon(r.categoryIcon);
        const isIncome = r.type === 'INCOME';
        const isSavings = r.type === 'SAVINGS';
        const isPositiveSavings = isSavings && r.amount > 0;

        const isPositive = isIncome || isPositiveSavings;

        return (
          <div key={r.id} className={styles.row} role="listitem">
            <span className={styles.rowIcon} aria-hidden="true">{icon}</span>
            <div className={styles.rowMain}>
              <div className={styles.rowTop}>
                <span className={styles.rowCategory} style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><RecurringIcon size={14} /> {r.categoryName}</span>
                <span className={`${styles.rowAmount} ${isPositive ? styles.positive : styles.negative}`}>
                  {isPositive ? '+' : '−'}{fmt(r.amount, r.currency)}
                </span>
              </div>
              <div className={styles.rowBottom}>
                <TypeBadge type={r.type} />
                <span className={styles.rowMeta}>{FREQ_LABEL[r.frequency]}</span>
                <span className={styles.rowDate}>Next: {r.nextDate}</span>
              </div>
            </div>
            <div className={styles.rowActions}>
              <button
                className={styles.actionBtn}
                title="Delete"
                aria-label={`Delete ${r.categoryName} recurrence`}
                onClick={() => handleDelete(r.id, r.categoryName)}
              >
                <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                  <path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                </svg>
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}

/* ── Root Component ────────────────────────────────────────────── */
export default function TransactionLog() {
  const [tab, setTab] = useState('all');
  const [categoriesMap, setCategoriesMap] = useState({});
  const [accountsMap, setAccountsMap] = useState({});
  const [loading, setLoading] = useState(true);

  const loadLookups = useCallback(async () => {
    try {
      const [catsRes, accsRes] = await Promise.all([
        axiosInstance.get('/v1/categories'),
        axiosInstance.get('/v1/accounts'),
      ]);
      const cats = {};
      catsRes.data.forEach((c) => {
        cats[c.id] = { name: c.name, icon: getCategoryIcon(c.icon), color: c.color };
      });
      const accs = {};
      accsRes.data.forEach((a) => {
        accs[a.id] = a.name;
      });
      setCategoriesMap(cats);
      setAccountsMap(accs);
    } catch (err) {
      console.error('Failed to load transaction log lookups', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadLookups();
  }, [loadLookups]);

  if (loading) {
    return (
      <div className={styles.container}>
        <div className={styles.loadingContainer}>
          <div className={styles.spinner} aria-hidden="true" />
          <span>Loading transactions…</span>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h2 className={styles.title}>Transactions</h2>
        <div className={styles.tabs} role="tablist">
          <button
            className={`${styles.tab} ${tab === 'all' ? styles.tabActive : ''}`}
            role="tab"
            aria-selected={tab === 'all'}
            id="tab-all"
            aria-controls="panel-all"
            onClick={() => setTab('all')}
          >
            All
          </button>
          <button
            className={`${styles.tab} ${tab === 'recurring' ? styles.tabActive : ''}`}
            role="tab"
            aria-selected={tab === 'recurring'}
            id="tab-recurring"
            aria-controls="panel-recurring"
            onClick={() => setTab('recurring')}
          >
            Recurring
          </button>
        </div>
      </div>

      <div
        id={tab === 'all' ? 'panel-all' : 'panel-recurring'}
        role="tabpanel"
        aria-labelledby={tab === 'all' ? 'tab-all' : 'tab-recurring'}
      >
        {tab === 'all' ? (
          <AllTab categoriesMap={categoriesMap} accountsMap={accountsMap} />
        ) : (
          <RecurringTab />
        )}
      </div>
    </div>
  );
}
