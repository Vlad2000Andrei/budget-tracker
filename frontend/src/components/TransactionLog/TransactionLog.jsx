import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import axiosInstance from '../../api/axiosInstance';
import { getCategoryIcon } from '../../api/utils';
import { useAuth } from '../../context/AuthContext';
import { useData } from '../../context/DataContext';
import AddTransactionModal from '../AddTransactionModal/AddTransactionModal';
import styles from './TransactionLog.module.css';

const TYPE_FILTER_OPTIONS = ['INCOME', 'EXPENSE', 'SAVINGS', 'MOVE'];

function fmt(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(Math.abs(amount));
}

function formatDateHeader(dateStr) {
  const parts = dateStr.split('-');
  if (parts.length !== 3) return dateStr;
  const year = parseInt(parts[0], 10);
  const month = parseInt(parts[1], 10) - 1;
  const day = parseInt(parts[2], 10);
  
  const dateObj = new Date(year, month, day);
  
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  
  const isToday = dateObj.toDateString() === today.toDateString();
  const isYesterday = dateObj.toDateString() === yesterday.toDateString();
  
  const options = { weekday: 'long', year: 'numeric', month: 'short', day: 'numeric' };
  const formatted = dateObj.toLocaleDateString('en-US', options);
  
  if (isToday) return `Today — ${formatted}`;
  if (isYesterday) return `Yesterday — ${formatted}`;
  return formatted;
}

function TypeBadge({ type }) {
  return (
    <span className={`${styles.typeBadge} ${styles[`type_${type}`]}`}>
      {type === 'INCOME' ? 'Income' : type === 'EXPENSE' ? 'Expense' : type === 'SAVINGS' ? 'Savings' : 'Move'}
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
function TransactionToast({ transaction: t, categoriesMap, accountsMap, onClose, onDeleted, onEdit }) {
  const { user } = useAuth();
  const defaultCurrency = user?.defaultCurrency || 'USD';
  const isMultiCurrency = t.currency && t.currency !== defaultCurrency;
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const [showDeleteOptions, setShowDeleteOptions] = useState(false);
  const [selectedDeleteMode, setSelectedDeleteMode] = useState('THIS_ONLY');
  const backdropRef = useRef(null);

  const isMove = t.type === 'MOVE';
  const category = !isMove ? (categoriesMap[t.categoryId] || { name: 'Unknown', icon: '📦' }) : null;
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

  async function handleDelete(mode = 'THIS_ONLY') {
    setDeleting(true);
    setDeleteError(null);
    try {
      await axiosInstance.delete(`/v1/transactions/${t.id}?mode=${mode}`);
      window.dispatchEvent(new Event('transaction-added'));
      onDeleted();
      onClose();
    } catch {
      setDeleteError('Failed to delete. Please try again.');
      setDeleting(false);
      setShowDeleteOptions(false);
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
            {isMove ? (
              <span className={styles.toastIconSvg} aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
                  <path d="M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z"/>
                </svg>
              </span>
            ) : (
              <span className={styles.toastIcon} aria-hidden="true">{category.icon}</span>
            )}
            <div>
              <div className={styles.toastCategory}>
                {isMove 
                  ? `Move: ${accountsMap[t.fromAccountId] || 'Unknown'} → ${accountsMap[t.toAccountId] || 'Unknown'}`
                  : (category.breadcrumb || category.name)}
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
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
          <div className={`${styles.toastAmount} ${isMove ? styles.neutralAmount : (isPositive ? styles.positive : styles.negative)}`}>
            {isMove ? '' : (isPositive ? '+' : '−')}{fmt(isMultiCurrency ? t.convertedAmount : t.amount, isMultiCurrency ? defaultCurrency : t.currency)}
          </div>
          {isMultiCurrency && (
            <div style={{ fontSize: '14px', color: 'var(--md-outline)', fontWeight: '500' }}>
              {isMove ? '' : (isPositive ? '+' : '−')}{fmt(t.amount, t.currency)}
            </div>
          )}
        </div>

        {/* Details grid */}
        <div className={styles.toastDetails}>
          <div className={styles.toastDetailRow}>
            <span className={styles.toastDetailLabel}>Date</span>
            <span className={styles.toastDetailValue}>{t.date.split('T')[0]}</span>
          </div>
          {isMove ? (
            <>
              <div className={styles.toastDetailRow}>
                <span className={styles.toastDetailLabel}>From Account</span>
                <span className={styles.toastDetailValue}>{accountsMap[t.fromAccountId]}</span>
              </div>
              <div className={styles.toastDetailRow}>
                <span className={styles.toastDetailLabel}>To Account</span>
                <span className={styles.toastDetailValue}>{accountsMap[t.toAccountId]}</span>
              </div>
            </>
          ) : (
            accountName && (
              <div className={styles.toastDetailRow}>
                <span className={styles.toastDetailLabel}>Account</span>
                <span className={styles.toastDetailValue}>{accountName}</span>
              </div>
            )
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
            className={styles.toastEditBtn}
            onClick={onEdit}
            disabled={deleting}
          >
            <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
              <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
            </svg>
            Edit
          </button>
          <button
            className={styles.toastDeleteBtn}
            onClick={t.recurrenceRuleId ? () => setShowDeleteOptions(true) : () => handleDelete('THIS_ONLY')}
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
                Delete
              </>
            )}
          </button>
        </div>

        {showDeleteOptions && (
          <div className={styles.choiceBackdrop} role="dialog" aria-modal="true" aria-label="Confirm deletion mode">
            <div className={styles.choiceToast}>
              <h3 className={styles.choiceTitle}>Delete Recurring Transaction</h3>
              <p className={styles.choiceSubtitle}>Choose how you want to delete this recurring transaction series:</p>
              
              <div className={styles.radioGroup}>
                <label className={styles.radioLabel}>
                  <input
                    type="radio"
                    name="deleteMode"
                    value="THIS_ONLY"
                    checked={selectedDeleteMode === 'THIS_ONLY'}
                    onChange={() => setSelectedDeleteMode('THIS_ONLY')}
                    className={styles.radioInput}
                  />
                  <span className={styles.radioText}>Just this occurrence</span>
                </label>

                <label className={styles.radioLabel}>
                  <input
                    type="radio"
                    name="deleteMode"
                    value="FUTURE"
                    checked={selectedDeleteMode === 'FUTURE'}
                    onChange={() => setSelectedDeleteMode('FUTURE')}
                    className={styles.radioInput}
                  />
                  <span className={styles.radioText}>This and all future occurrences</span>
                </label>

                <label className={styles.radioLabel}>
                  <input
                    type="radio"
                    name="deleteMode"
                    value="ALL"
                    checked={selectedDeleteMode === 'ALL'}
                    onChange={() => setSelectedDeleteMode('ALL')}
                    className={styles.radioInput}
                  />
                  <span className={styles.radioText}>All occurrences in the series</span>
                </label>
              </div>

              <div className={styles.choiceActions}>
                <button 
                  type="button" 
                  className={styles.choiceCancelBtn} 
                  onClick={() => setShowDeleteOptions(false)}
                  disabled={deleting}
                >
                  Cancel
                </button>
                <button 
                  type="button" 
                  className={styles.choiceConfirmBtn} 
                  onClick={() => handleDelete(selectedDeleteMode)}
                  disabled={deleting}
                >
                  {deleting ? 'Deleting...' : 'Delete'}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* ── All Transactions Tab ──────────────────────────────────────── */
function AllTab({ categoriesMap, accountsMap }) {
  const { user } = useAuth();
  const defaultCurrency = user?.defaultCurrency || 'USD';
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState('All');
  const [accountFilter, setAccountFilter] = useState('All');
  const [search, setSearch] = useState('');
  const [searchExpanded, setSearchExpanded] = useState(false);
  const searchInputRef = useRef(null);
  const [selectedTx, setSelectedTx] = useState(null);
  const [editingTx, setEditingTx] = useState(null);
  const [monthsToLoad, setMonthsToLoad] = useState(1);
  const [hasOlder, setHasOlder] = useState(false);

  const getStartDateForMonthsAgo = useCallback((monthsAgo) => {
    const date = new Date();
    const year = date.getFullYear();
    const month = date.getMonth();
    
    const targetDate = new Date(year, month - (monthsAgo - 1), 1);
    
    const yyyy = targetDate.getFullYear();
    const mm = String(targetDate.getMonth() + 1).padStart(2, '0');
    const dd = '01';
    return `${yyyy}-${mm}-${dd}`;
  }, []);

  const getNextMonthLabel = useCallback((monthsAgo) => {
    const date = new Date();
    const year = date.getFullYear();
    const month = date.getMonth();
    
    const targetDate = new Date(year, month - monthsAgo, 1);
    const targetYear = targetDate.getFullYear();
    const monthName = targetDate.toLocaleDateString('en-US', { month: 'long' });
    
    if (targetYear !== year) {
      return `Load ${monthName} ${targetYear}`;
    }
    return `Load ${monthName}`;
  }, []);

  const loadTransactions = useCallback(async () => {
    try {
      const start = getStartDateForMonthsAgo(monthsToLoad);
      const res = await axiosInstance.get(`/v1/transactions?startDate=${start}`);
      setTransactions(res.data);
      const hasOlderHeader = res.headers['x-has-older-transactions'] === 'true';
      setHasOlder(hasOlderHeader);
    } catch (err) {
      console.error('Failed to load transactions', err);
    } finally {
      setLoading(false);
    }
  }, [monthsToLoad, getStartDateForMonthsAgo]);

  useEffect(() => {
    loadTransactions();
    const handleRefresh = () => loadTransactions();
    window.addEventListener('transaction-added', handleRefresh);
    return () => {
      window.removeEventListener('transaction-added', handleRefresh);
    };
  }, [loadTransactions]);

  useEffect(() => {
    if (searchExpanded && searchInputRef.current) {
      searchInputRef.current.focus();
    }
  }, [searchExpanded]);

  const handleSearchClick = () => {
    if (searchExpanded) {
      if (search) {
        setSearch('');
      } else {
        setSearchExpanded(false);
      }
    } else {
      setSearchExpanded(true);
    }
  };

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
    
    if (accountFilter !== 'All') {
      const isMove = t.type === 'MOVE';
      if (isMove) {
        const fromMatch = t.fromAccountId ? String(t.fromAccountId) === accountFilter : false;
        const toMatch = t.toAccountId ? String(t.toAccountId) === accountFilter : false;
        if (!fromMatch && !toMatch) return false;
      } else {
        const match = t.accountId ? String(t.accountId) === accountFilter : false;
        if (!match) return false;
      }
    }

    const isMove = t.type === 'MOVE';
    if (search) {
      const query = search.toLowerCase();
      if (isMove) {
        const fromName = accountsMap[t.fromAccountId]?.toLowerCase() || '';
        const toName = accountsMap[t.toAccountId]?.toLowerCase() || '';
        const notes = t.notes?.toLowerCase() || '';
        if (!fromName.includes(query) && !toName.includes(query) && !notes.includes(query)) return false;
      } else {
        const catName = (categoriesMap[t.categoryId]?.breadcrumb || categoriesMap[t.categoryId]?.name || '').toLowerCase();
        const notes = t.notes?.toLowerCase() || '';
        if (!catName.includes(query) && !notes.includes(query)) return false;
      }
    }
    return true;
  });

  return (
    <>
      <div>
        {/* Filter bar */}
        <div className={styles.filterBar}>
          <div className={`${styles.searchWrap} ${searchExpanded || search ? styles.searchWrapExpanded : ''}`}>
            <button
              type="button"
              className={styles.searchToggleBtn}
              onClick={handleSearchClick}
              aria-label="Toggle search"
            >
              <svg className={styles.searchIcon} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
              {!(searchExpanded || search) && <span className={styles.searchBtnText}>Search</span>}
            </button>
            <input
              ref={searchInputRef}
              type="search"
              placeholder="Search..."
              className={styles.searchInput}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onBlur={() => {
                if (!search) setSearchExpanded(false);
              }}
              aria-label="Search transactions"
            />
          </div>
          
          <span className={styles.filterGroupLabel}>Type</span>
          {TYPE_FILTER_OPTIONS.map((opt) => (
            <button
              key={opt}
              className={`${styles.chip} ${typeFilter === opt ? styles.chipActive : ''}`}
              onClick={() => setTypeFilter(typeFilter === opt ? 'All' : opt)}
              aria-pressed={typeFilter === opt}
            >
              {opt.charAt(0).toUpperCase() + opt.slice(1).toLowerCase()}
            </button>
          ))}

          <div className={styles.filterDivider} aria-hidden="true" />

          <span className={styles.filterGroupLabel}>Account</span>
          {Object.entries(accountsMap).map(([id, name]) => (
            <button
              key={id}
              className={`${styles.chip} ${accountFilter === id ? styles.chipActive : ''}`}
              onClick={() => setAccountFilter(accountFilter === id ? 'All' : id)}
              aria-pressed={accountFilter === id}
            >
              {name}
            </button>
          ))}
        </div>

        {/* Rows */}
        <div className={styles.list} role="list">
          {filtered.length === 0 && (
            <div className={styles.empty}>No transactions found.</div>
          )}
          {(() => {
            let lastDate = null;
            return filtered.map((t) => {
              const dateOnly = t.date.split('T')[0];
              const showHeader = dateOnly !== lastDate;
              if (showHeader) {
                lastDate = dateOnly;
              }

              const isMove = t.type === 'MOVE';
              const category = !isMove ? (categoriesMap[t.categoryId] || { name: 'Unknown', icon: '📦' }) : null;
              const accountName = t.accountId ? accountsMap[t.accountId] : null;
              const isIncome = t.type === 'INCOME';
              const isSavings = t.type === 'SAVINGS';
              const isPositiveSavings = isSavings && t.amount > 0;

              const isPositive = isIncome || isPositiveSavings;
              const isMultiCurrency = t.currency && t.currency !== defaultCurrency;

              const fromAccountName = isMove ? accountsMap[t.fromAccountId] : null;
              const toAccountName = isMove ? accountsMap[t.toAccountId] : null;
              const rowLabel = isMove 
                ? `Move: ${fromAccountName || 'Unknown'} → ${toAccountName || 'Unknown'}`
                : (category.breadcrumb || category.name);

              return (
                <div key={t.id} style={{ width: '100%' }}>
                  {showHeader && (
                    <div className={styles.dateSeparator}>
                      <span className={styles.dateText}>{formatDateHeader(dateOnly)}</span>
                    </div>
                  )}
                  <button
                    className={styles.rowBtn}
                    onClick={() => setSelectedTx(t)}
                    aria-label={`View details for ${isMove ? 'move' : category.name} transaction`}
                  >
                    {isMove ? (
                      <span className={styles.rowIconSvg} aria-hidden="true">
                        <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                          <path d="M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z"/>
                        </svg>
                      </span>
                    ) : (
                      <span className={styles.rowIcon} aria-hidden="true">{category.icon}</span>
                    )}
                    <div className={styles.rowMain}>
                      <div className={styles.rowTop}>
                        <span className={styles.rowCategory}>
                          {rowLabel}
                          {t.recurrenceRuleId && (
                            <span className={styles.recurringBadge}>
                              <RecurringIcon />
                            </span>
                          )}
                        </span>
                        <div className={styles.amountCol}>
                          <span className={`${styles.rowAmount} ${isMove ? styles.neutralAmount : (isPositive ? styles.positive : styles.negative)}`}>
                            {isMove ? '' : (isPositive ? '+' : '−')}{fmt(isMultiCurrency ? t.convertedAmount : t.amount, isMultiCurrency ? defaultCurrency : t.currency)}
                          </span>
                          {isMultiCurrency && (
                            <span className={styles.rowAmountSub}>
                              {isMove ? '' : (isPositive ? '+' : '−')}{fmt(t.amount, t.currency)}
                            </span>
                          )}
                        </div>
                      </div>
                      {t.notes && (
                        <div className={styles.rowNotesInline} title={t.notes}>
                          <svg className={styles.rowNoteIcon} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                            <path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                          </svg>
                          <span>{t.notes}</span>
                        </div>
                      )}
                      <div className={styles.rowBottom}>
                        <TypeBadge type={t.type} />
                        {!isMove && accountName && <span className={styles.rowMeta}>{accountName}</span>}
                      </div>
                    </div>
                    <svg className={styles.rowChevron} viewBox="0 0 24 24" fill="currentColor" width="16" height="16" aria-hidden="true">
                      <path d="M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>
                    </svg>
                  </button>
                </div>
              );
            });
          })()}
          {hasOlder && (
            <div className={styles.loadMoreContainer}>
              <button
                type="button"
                className={styles.loadMoreBtn}
                onClick={() => setMonthsToLoad((prev) => prev + 1)}
              >
                {getNextMonthLabel(monthsToLoad)}
              </button>
            </div>
          )}
        </div>
      </div>

      {selectedTx && (
        <TransactionToast
          transaction={selectedTx}
          categoriesMap={categoriesMap}
          accountsMap={accountsMap}
          onClose={() => setSelectedTx(null)}
          onDeleted={loadTransactions}
          onEdit={() => {
            setEditingTx(selectedTx);
            setSelectedTx(null);
          }}
        />
      )}

      {editingTx && (
        <AddTransactionModal
          transaction={editingTx}
          onClose={() => setEditingTx(null)}
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
  const { categories, accounts, fetchInitialData } = useData();

  useEffect(() => {
    fetchInitialData();
  }, [fetchInitialData]);

  const categoriesMap = useMemo(() => {
    const rawCats = {};
    categories.forEach((c) => {
      rawCats[c.id] = c;
    });

    const getBreadcrumb = (catId) => {
      const path = [];
      let curr = rawCats[catId];
      let depth = 0;
      while (curr && depth < 20) {
        path.unshift(curr.name);
        curr = curr.parentId ? rawCats[curr.parentId] : null;
        depth++;
      }
      return path.join(' › ');
    };

    const cats = {};
    categories.forEach((c) => {
      cats[c.id] = {
        name: c.name,
        breadcrumb: getBreadcrumb(c.id),
        icon: getCategoryIcon(c.icon),
        color: c.color
      };
    });
    return cats;
  }, [categories]);

  const accountsMap = useMemo(() => {
    const accs = {};
    accounts.forEach((a) => {
      accs[a.id] = a.name;
    });
    return accs;
  }, [accounts]);

  const loading = categories.length === 0 || accounts.length === 0;

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
