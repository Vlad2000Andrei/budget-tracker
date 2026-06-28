import { useEffect, useRef, useState, useMemo } from 'react';
import axiosInstance from '../../api/axiosInstance';
import { useAuth } from '../../context/AuthContext';
import { useData } from '../../context/DataContext';
import { getCategoryIcon } from '../../api/utils';
import styles from './AddTransactionModal.module.css';

const FREQ_OPTIONS = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'];
const FREQ_LABELS = { DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly' };

const today = () => new Date().toISOString().slice(0, 10);

export default function AddTransactionModal({ onClose, transaction }) {
  const { user } = useAuth();
  const [form, setForm] = useState({
    type: transaction?.linkedTransactionId ? 'MOVE' : (transaction?.type || 'EXPENSE'),
    amount: transaction ? Math.abs(transaction.amount).toString() : '',
    currency: transaction?.currency || user?.defaultCurrency || 'USD',
    exchangeRate: transaction?.exchangeRate || '',
    categoryId: transaction?.categoryId || null,
    date: transaction?.date ? transaction.date.split('T')[0] : today(),
    accountId: transaction?.accountId || '',
    notes: transaction?.notes || '',
    makeRecurring: !!transaction?.recurrenceRule,
    frequency: transaction?.recurrenceRule?.frequency || 'MONTHLY',
    interval: transaction?.recurrenceRule?.interval || 1,
    recurringStartDate: transaction?.recurrenceRule?.startDate || today(),
    endDate: transaction?.recurrenceRule?.endDate || '',
    fromAccountId: transaction?.fromAccountId?.toString() || '',
    toAccountId: transaction?.toAccountId?.toString() || '',
  });

  const { categories: dbCategories, accounts: dbAccounts, fetchInitialData } = useData();
  const [categorySearch, setCategorySearch] = useState('');
  const [showCategorySearchInput, setShowCategorySearchInput] = useState(false);
  const [notesExpanded, setNotesExpanded] = useState(!!transaction?.notes);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [savingsAction, setSavingsAction] = useState(
    transaction?.type === 'SAVINGS' && transaction.amount < 0 ? 'WITHDRAWAL' : 'DEPOSIT'
  );
  const [showEndDate, setShowEndDate] = useState(!!transaction?.recurrenceRule?.endDate);
  const amountRef = useRef(null);
  const backdropRef = useRef(null);
  const categorySearchInputRef = useRef(null);

  useEffect(() => {
    if (showCategorySearchInput && categorySearchInputRef.current) {
      categorySearchInputRef.current.focus();
    }
  }, [showCategorySearchInput]);

  const formatCurrency = (amount, currency) => {
    try {
      return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
    } catch {
      return (amount || 0).toFixed(2) + ' ' + currency;
    }
  };

  // Fetch categories, accounts, and savings goals on mount
  useEffect(() => {
    async function loadData() {
      try {
        await fetchInitialData();

        // If editing an existing SAVINGS transaction, find its from/to accounts
        if (transaction && transaction.type === 'SAVINGS') {
          const goalsRes = await axiosInstance.get('/v1/savings-goals');
          const goal = goalsRes.data.find(g => g.categoryId === transaction.categoryId);
          if (goal) {
            const txsRes = await axiosInstance.get(`/v1/savings-goals/${goal.id}/transactions`);
            const matchedSgTx = txsRes.data.find(t => t.transactionId === transaction.id);
            if (matchedSgTx) {
              setForm(f => ({
                ...f,
                fromAccountId: matchedSgTx.fromAccountId || '',
                toAccountId: matchedSgTx.toAccountId || '',
              }));
              setSavingsAction(matchedSgTx.type);
            }
          }
        }
      } catch (err) {
        console.error("Failed to load data in transaction modal", err);
      }
    }
    loadData();
  }, [transaction, fetchInitialData]);

  // Update default currency when user info becomes available
  useEffect(() => {
    if (user?.defaultCurrency && !transaction) {
      setForm(f => ({ ...f, currency: user.defaultCurrency }));
    }
  }, [user, transaction]);

  // Auto-focus amount on open
  useEffect(() => {
    const timer = setTimeout(() => amountRef.current?.focus(), 350);
    return () => clearTimeout(timer);
  }, []);

  // Close on Escape
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  // Trap scroll on body
  useEffect(() => {
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = ''; };
  }, []);

  const set = (field, value) => setForm((f) => ({ ...f, [field]: value }));

  // Dynamically group categories by type
  const CATEGORIES_BY_TYPE = useMemo(() => {
    const result = { EXPENSE: [], INCOME: [], SAVINGS: [] };
    const catMap = new Map(dbCategories.map(c => [c.id, c]));

    dbCategories.forEach(c => {
      const parent = c.parentId ? catMap.get(c.parentId) : null;
      const mapped = {
        id: c.id,
        name: c.name,
        icon: getCategoryIcon(c.icon),
        parentName: parent ? parent.name : null,
      };
      if (result[c.type]) {
        result[c.type].push(mapped);
      }
    });
    return result;
  }, [dbCategories]);

  // Dynamically map accounts
  const ACCOUNTS = useMemo(() => {
    return dbAccounts
      .map(a => ({
        id: a.id,
        name: a.name,
        type: a.type,
        currency: a.currency,
        balance: a.balance,
      }))
      .sort((a, b) => {
        if (a.type === 'CHECKING' && b.type !== 'CHECKING') return -1;
        if (a.type !== 'CHECKING' && b.type === 'CHECKING') return 1;
        return a.name.localeCompare(b.name);
      });
  }, [dbAccounts]);

  const categories = CATEGORIES_BY_TYPE[form.type] ?? [];
  const recentChips = categories.slice(0, 10);
  const searchResults = categorySearch.length >= 2
    ? categories.filter((c) =>
        c.name.toLowerCase().includes(categorySearch.toLowerCase()) ||
        (c.parentName ?? '').toLowerCase().includes(categorySearch.toLowerCase())
      )
    : [];

  const selectedCategory = categories.find((c) => c.id === form.categoryId);
  const showExchangeRate = form.currency !== (user?.defaultCurrency || 'USD');

  async function handleSave() {
    if (!form.amount || (form.type !== 'MOVE' && !form.categoryId)) return;

    setSaving(true);
    setSaveError(null);

    const activeDate = form.makeRecurring ? form.recurringStartDate : form.date;

    if (form.type === 'MOVE') {
      if (!form.fromAccountId || !form.toAccountId) {
        setSaveError("Both From and To accounts are required for moves.");
        setSaving(false);
        return;
      }

      const payload = {
        fromAccountId: parseInt(form.fromAccountId, 10),
        toAccountId: parseInt(form.toAccountId, 10),
        amount: Math.abs(parseFloat(form.amount)),
        currency: form.currency,
        date: new Date(activeDate).toISOString(),
        notes: form.notes || undefined,
      };

      try {
        if (transaction) {
          await axiosInstance.delete(`/v1/transactions/${transaction.id}`);
        }
        await axiosInstance.post('/v1/transactions/transfer', payload);
        window.dispatchEvent(new Event('transaction-added'));
        onClose();
      } catch (err) {
        setSaveError(err.response?.data?.message || err.message);
        setSaving(false);
      }
      return;
    }

    if (form.type === 'SAVINGS') {
      if (!form.fromAccountId || !form.toAccountId) {
        setSaveError("Both From and To accounts are required for savings transactions.");
        setSaving(false);
        return;
      }

      const payload = {
        fromAccountId: parseInt(form.fromAccountId, 10),
        toAccountId: parseInt(form.toAccountId, 10),
        amount: Math.abs(parseFloat(form.amount)),
        currency: form.currency,
        type: savingsAction,
        date: new Date(activeDate).toISOString(),
        notes: form.notes || undefined,
        categoryId: form.categoryId ? parseInt(form.categoryId, 10) : undefined,
      };

      try {
        if (transaction) {
          await axiosInstance.delete(`/v1/transactions/${transaction.id}`);
        }
        await axiosInstance.post('/v1/transactions/savings', payload);
        window.dispatchEvent(new Event('transaction-added'));
        onClose();
      } catch (err) {
        setSaveError(err.response?.data?.message || err.message);
        setSaving(false);
      }
      return;
    }

    const payload = {
      categoryId: form.categoryId,
      accountId: form.accountId ? parseInt(form.accountId, 10) : undefined,
      amount: Math.abs(parseFloat(form.amount)),
      currency: form.currency,
      type: form.type,
      notes: form.notes || undefined,
      date: new Date(activeDate).toISOString(),
    };

    if (form.makeRecurring) {
      payload.recurrenceRule = {
        frequency: form.frequency,
        interval: parseInt(form.interval, 10),
        startDate: form.recurringStartDate || activeDate,
        endDate: form.endDate || undefined,
      };
    }

    try {
      if (transaction) {
        await axiosInstance.patch(`/v1/transactions/${transaction.id}`, payload);
      } else {
        await axiosInstance.post('/v1/transactions', payload);
      }
      window.dispatchEvent(new Event('transaction-added'));
      onClose();
    } catch (err) {
      setSaveError(err.message);
      setSaving(false);
    }
  }

  const canSave = form.amount && !saving && (
    (form.type === 'MOVE' && form.fromAccountId && form.toAccountId) ||
    (form.type !== 'MOVE' && form.categoryId && (form.type !== 'SAVINGS' || (form.fromAccountId && form.toAccountId)))
  );

  return (
    <div
      className={styles.backdrop}
      ref={backdropRef}
      onClick={(e) => { if (e.target === backdropRef.current) onClose(); }}
      role="dialog"
      aria-modal="true"
      aria-label="Add transaction"
    >
      <div className={styles.sheet}>
        {/* Drag handle */}
        <div className={styles.handle} aria-hidden="true" />

        <div className={styles.content}>
          {/* Title + close */}
          <div className={styles.sheetHeader}>
            <h2 className={styles.sheetTitle}>
              {transaction ? 'Edit Transaction' : 'Add Transaction'}
            </h2>
            <button className={styles.closeBtn} onClick={onClose} aria-label="Close">
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
              </svg>
            </button>
          </div>

          {/* ── Type toggle ────────────────────────────────────── */}
          <div className={styles.field}>
            <div className={styles.typeToggle} role="group" aria-label="Transaction type">
              {['EXPENSE', 'INCOME', 'SAVINGS', 'MOVE'].map((t) => {
                const getIcon = () => {
                  switch (t) {
                    case 'EXPENSE':
                      return (
                        <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                          <path d="M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z"/>
                        </svg>
                      );
                    case 'INCOME':
                      return (
                        <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                          <path d="M4 12l1.41 1.41L11 6.83V20h2V6.83l5.58 5.59L20 12l-8-8-8 8z"/>
                        </svg>
                      );
                    case 'SAVINGS':
                      return (
                        <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                          <path d="M4 10v7h3v-7H4zm6 0v7h3v-7h-3zM2 22h19v-3H2v3zm14-12v7h3v-7h-3zm-4.5-9L2 6v2h19V6l-9.5-5z"/>
                        </svg>
                      );
                    case 'MOVE':
                      return (
                        <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                          <path d="M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z"/>
                        </svg>
                      );
                    default:
                      return null;
                  }
                };

                const getLabel = () => {
                  switch (t) {
                    case 'EXPENSE': return 'Expense';
                    case 'INCOME': return 'Income';
                    case 'SAVINGS': return 'Savings';
                    case 'MOVE': return 'Move';
                    default: return '';
                  }
                };

                return (
                  <button
                    key={t}
                    className={`${styles.typeBtn} ${form.type === t ? styles[`typeBtnActive_${t}`] : ''}`}
                    onClick={() => {
                      setForm(f => ({
                        ...f,
                        type: t,
                        categoryId: null,
                        fromAccountId: '',
                        toAccountId: '',
                      }));
                      setCategorySearch('');
                      setSavingsAction('DEPOSIT');
                    }}
                    aria-pressed={form.type === t}
                  >
                    {getIcon()}
                    <span>{getLabel()}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* ── Savings Action Toggle (Visible only if type is SAVINGS) ── */}
          {form.type === 'SAVINGS' && (
            <div className={styles.field} style={{ animation: 'fadeSlide 200ms var(--md-easing-decelerate) both' }}>
              <label className={styles.label}>Savings Action</label>
              <div className={styles.typeToggle} role="group" aria-label="Savings action">
                {['DEPOSIT', 'WITHDRAWAL'].map((act) => (
                  <button
                    key={act}
                    className={`${styles.typeBtn} ${savingsAction === act ? styles[`typeBtnActive_${act}`] : ''}`}
                    onClick={() => {
                      setSavingsAction(act);
                      if (form.fromAccountId && form.toAccountId) {
                        setForm(f => ({
                          ...f,
                          fromAccountId: f.toAccountId,
                          toAccountId: f.fromAccountId
                        }));
                      }
                    }}
                    aria-pressed={savingsAction === act}
                  >
                    {act === 'DEPOSIT' ? '🏦 Deposit' : '💸 Withdrawal'}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* ── Amount + Currency ──────────────────────────────── */}
          <div className={styles.row2}>
            <div className={`${styles.field} ${styles.fieldFlex}`}>
              <label className={styles.label} htmlFor="modal-amount">Amount</label>
              <input
                id="modal-amount"
                ref={amountRef}
                type="number"
                min="0"
                step="0.01"
                placeholder="0.00"
                className={styles.amountInput}
                value={form.amount}
                onChange={(e) => set('amount', e.target.value)}
              />
            </div>
            <div className={`${styles.field} ${styles.fieldSmall}`}>
              <label className={styles.label} htmlFor="modal-currency">Currency</label>
              <select
                id="modal-currency"
                className={`${styles.select} ${styles.currencySelect}`}
                value={form.currency}
                onChange={(e) => set('currency', e.target.value)}
              >
                {['USD', 'EUR', 'GBP', 'CAD', 'RON', 'JPY', 'CHF'].map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
          </div>

          {/* ── Exchange Rate (visible only if currency ≠ USD) ── */}
          {showExchangeRate && (
            <div className={`${styles.field} ${styles.exchangeField}`}>
              <label className={styles.label} htmlFor="modal-exchange-rate">
                Exchange Rate <span className={styles.labelHint}>(1 {form.currency} = ? {user?.defaultCurrency || 'USD'})</span>
              </label>
              <input
                id="modal-exchange-rate"
                type="number"
                min="0"
                step="0.000001"
                placeholder="e.g. 0.9200"
                className={styles.input}
                value={form.exchangeRate}
                onChange={(e) => set('exchangeRate', e.target.value)}
              />
            </div>
          )}

          {/* ── Category ──────────────────────────────────────── */}
          {form.type !== 'MOVE' && (
            <div className={styles.field}>
              <label className={styles.label}>Category</label>

              {/* Recent chips */}
              <div className={styles.categoryChips} role="group" aria-label="Recent categories">
                <div className={styles.stickySearchContainer}>
                  <div className={`${styles.searchWrap} ${showCategorySearchInput || categorySearch ? styles.searchWrapExpanded : ''}`}>
                    <button
                      type="button"
                      className={styles.searchToggleBtn}
                      onClick={() => {
                        const nextVal = !showCategorySearchInput;
                        setShowCategorySearchInput(nextVal);
                        if (!nextVal) setCategorySearch('');
                      }}
                      aria-label="Search categories"
                    >
                      <svg className={styles.searchIcon} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <circle cx="11" cy="11" r="8"></circle>
                        <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                      </svg>
                      {!(showCategorySearchInput || categorySearch) && <span className={styles.searchBtnText}>Search</span>}
                    </button>
                    <input
                      ref={categorySearchInputRef}
                      type="search"
                      placeholder="Search categories…"
                      className={styles.searchInput}
                      value={categorySearch}
                      onChange={(e) => setCategorySearch(e.target.value)}
                      onBlur={() => {
                        if (!categorySearch) setShowCategorySearchInput(false);
                      }}
                    />
                  </div>
                </div>
                {recentChips.map((c) => (
                  <button
                    key={c.id}
                    className={`${styles.chip} ${form.categoryId === c.id ? styles.chipActive : ''}`}
                    onClick={() => {
                      set('categoryId', c.id);
                      setCategorySearch('');
                      setShowCategorySearchInput(false);
                    }}
                    aria-pressed={form.categoryId === c.id}
                  >
                    {c.icon} {c.name}
                  </button>
                ))}
              </div>

              {/* Search results */}
              {searchResults.length > 0 && (
                <ul className={styles.searchResults} role="listbox" aria-label="Category suggestions">
                  {searchResults.map((c) => (
                    <li key={c.id}>
                      <button
                        className={styles.searchResultItem}
                        onClick={() => {
                          set('categoryId', c.id);
                          setCategorySearch('');
                          setShowCategorySearchInput(false);
                        }}
                        role="option"
                        aria-selected={form.categoryId === c.id}
                      >
                        <span>{c.icon}</span>
                        <span>
                          {c.parentName && <span className={styles.breadcrumb}>{c.parentName} › </span>}
                          {c.name}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              {selectedCategory && (
                <p className={styles.selectedCategory}>
                  ✓ {selectedCategory.icon} {selectedCategory.parentName ? `${selectedCategory.parentName} › ` : ''}{selectedCategory.name}
                </p>
              )}
            </div>
          )}

          {/* ── Date ──────────────────────────────────────────── */}
          {!form.makeRecurring && (
            <div className={styles.field}>
              <label className={styles.label} htmlFor="modal-date">Date</label>
              <input
                id="modal-date"
                type="date"
                className={styles.input}
                value={form.date}
                onChange={(e) => set('date', e.target.value)}
              />
            </div>
          )}

          {/* ── Account Selector(s) ────────────────────────────── */}
          {(form.type === 'SAVINGS' || form.type === 'MOVE') ? (
            <>
              {/* From Account */}
              <div className={styles.field}>
                <label htmlFor="modal-from-account" className={styles.label}>
                  From Account <span style={{ color: 'var(--md-error)' }}>*</span>
                </label>
                {ACCOUNTS.length === 0 ? (
                  <p className={styles.labelHint}>⚠️ No accounts found. Please create an account first.</p>
                ) : (
                  <select
                    id="modal-from-account"
                    className={styles.select}
                    value={form.fromAccountId}
                    onChange={(e) => {
                      const val = e.target.value;
                      setForm(f => {
                        const next = { ...f, fromAccountId: val };
                        if (val && val === f.toAccountId) {
                          next.toAccountId = '';
                        }
                        if (f.type === 'MOVE' && val && f.toAccountId) {
                          const srcAcc = ACCOUNTS.find(a => a.id.toString() === val);
                          const destAcc = ACCOUNTS.find(a => a.id.toString() === f.toAccountId.toString());
                          if (srcAcc && destAcc && srcAcc.type !== destAcc.type) {
                            next.toAccountId = '';
                          }
                        }
                        return next;
                      });
                    }}
                    required
                  >
                    <option value="">-- Select Source Account --</option>
                    {ACCOUNTS
                      .filter(a => form.type === 'MOVE' ? true : (savingsAction === 'DEPOSIT' ? a.type !== 'SAVINGS' : a.type === 'SAVINGS'))
                      .map(a => (
                        <option key={a.id} value={a.id}>
                          {a.name} ({a.currency} — Balance: {formatCurrency(a.balance, a.currency)})
                        </option>
                      ))}
                  </select>
                )}
              </div>

              {/* To Account */}
              <div className={styles.field}>
                <label htmlFor="modal-to-account" className={styles.label}>
                  To Account <span style={{ color: 'var(--md-error)' }}>*</span>
                </label>
                {ACCOUNTS.length < 2 ? (
                  <p className={styles.labelHint}>⚠️ You need at least 2 accounts to perform a transfer.</p>
                ) : (
                  <select
                    id="modal-to-account"
                    className={styles.select}
                    value={form.toAccountId}
                    onChange={(e) => set('toAccountId', e.target.value)}
                    required
                  >
                    <option value="">-- Select Destination Account --</option>
                    {ACCOUNTS
                      .filter(a => a.id.toString() !== form.fromAccountId?.toString())
                      .filter(a => {
                        if (form.type === 'MOVE') {
                          if (!form.fromAccountId) return true;
                          const fromAcc = ACCOUNTS.find(src => src.id.toString() === form.fromAccountId.toString());
                          return fromAcc ? a.type === fromAcc.type : true;
                        } else {
                          return savingsAction === 'DEPOSIT' ? a.type === 'SAVINGS' : a.type !== 'SAVINGS';
                        }
                      })
                      .map(a => (
                        <option key={a.id} value={a.id}>
                          {a.name} ({a.currency} — Balance: {formatCurrency(a.balance, a.currency)})
                        </option>
                      ))}
                  </select>
                )}
              </div>
            </>
          ) : (
            <div className={styles.field}>
              <span className={styles.label}>
                Account <span className={styles.labelHint}>optional</span>
              </span>
              <div className={styles.chips} role="group" aria-label="Account selection">
                {ACCOUNTS.map((a) => {
                  const isSelected = form.accountId == a.id;
                  return (
                    <button
                      key={a.id}
                      type="button"
                      className={`${styles.chip} ${isSelected ? styles.chipActive : ''}`}
                      onClick={() => set('accountId', isSelected ? '' : a.id)}
                      aria-pressed={isSelected}
                    >
                      {a.type === 'CHECKING' ? '💳' : '💰'} {a.name}
                    </button>
                  );
                })}
                {ACCOUNTS.length === 0 && (
                  <span className={styles.labelHint}>No accounts available</span>
                )}
              </div>
            </div>
          )}

          {/* ── Notes (collapsible) ───────────────────────────── */}
          <div className={styles.field}>
            <button
              className={styles.expandToggle}
              onClick={() => setNotesExpanded((v) => !v)}
              aria-expanded={notesExpanded}
            >
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"
                style={{ transform: notesExpanded ? 'rotate(90deg)' : 'none', transition: 'transform 200ms' }}>
                <path d="M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
              </svg>
              Notes <span className={styles.labelHint}>optional</span>
            </button>
            {notesExpanded && (
              <textarea
                id="modal-notes"
                className={styles.textarea}
                placeholder="Add a note…"
                rows={3}
                value={form.notes}
                onChange={(e) => set('notes', e.target.value)}
              />
            )}
          </div>

          {/* ── Make Recurring ────────────────────────────────── */}
          <div className={styles.field}>
            <button
              className={styles.expandToggle}
              onClick={() => {
                const nextVal = !form.makeRecurring;
                setForm(f => ({
                  ...f,
                  makeRecurring: nextVal,
                  recurringStartDate: nextVal ? f.date : f.recurringStartDate
                }));
              }}
              aria-expanded={form.makeRecurring}
            >
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"
                style={{ transform: form.makeRecurring ? 'rotate(90deg)' : 'none', transition: 'transform 200ms' }}>
                <path d="M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
              </svg>
              <svg viewBox="0 0 24 24" fill="currentColor" width="15" height="15" style={{ verticalAlign: 'middle', color: 'var(--md-primary)' }} aria-hidden="true">
                <path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 0 0 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/>
              </svg>{' '}
              Make Recurring <span className={styles.labelHint}>optional</span>
            </button>

            {form.makeRecurring && (
              <div className={styles.recurringFields}>
                <div className={styles.row2}>
                  <div className={`${styles.field} ${styles.fieldFlex}`}>
                    <label className={styles.label} htmlFor="modal-frequency">Frequency</label>
                    <select
                      id="modal-frequency"
                      className={styles.select}
                      value={form.frequency}
                      onChange={(e) => set('frequency', e.target.value)}
                    >
                      {FREQ_OPTIONS.map((f) => (
                        <option key={f} value={f}>{FREQ_LABELS[f]}</option>
                      ))}
                    </select>
                  </div>
                  <div className={`${styles.field} ${styles.fieldSmall}`}>
                    <label className={styles.label} htmlFor="modal-interval">Every</label>
                    <input
                      id="modal-interval"
                      type="number"
                      min="1"
                      max="365"
                      className={styles.input}
                      value={form.interval}
                      onChange={(e) => set('interval', e.target.value)}
                    />
                  </div>
                </div>
                <div className={styles.field}>
                  <label className={styles.label} htmlFor="modal-recurring-start-date">
                    Recurrence Start Date <span className={styles.labelHint}>we schedule future copies from this date — defaults to the transaction date above</span>
                  </label>
                  <input
                    id="modal-recurring-start-date"
                    type="date"
                    className={styles.input}
                    value={form.recurringStartDate}
                    onChange={(e) => set('recurringStartDate', e.target.value)}
                  />
                </div>
                {showEndDate ? (
                  <div className={styles.field} style={{ animation: 'fadeSlide 200ms var(--md-easing-decelerate) both' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <label className={styles.label} htmlFor="modal-end-date">End Date</label>
                      <button
                        type="button"
                        className={styles.removeEndDateBtn}
                        onClick={() => {
                          setShowEndDate(false);
                          set('endDate', '');
                        }}
                      >
                        Remove
                      </button>
                    </div>
                    <input
                      id="modal-end-date"
                      type="date"
                      className={styles.input}
                      value={form.endDate}
                      min={form.recurringStartDate || form.date}
                      onChange={(e) => set('endDate', e.target.value)}
                    />
                  </div>
                ) : (
                  <div className={styles.field}>
                    <button
                      type="button"
                      className={styles.addEndDateBtn}
                      onClick={() => setShowEndDate(true)}
                    >
                      <span aria-hidden="true">+</span> Add End Date
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* ── Error ─────────────────────────────────────────── */}
          {saveError && (
            <div className={styles.errorBanner} role="alert">
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
              </svg>
              {saveError}
            </div>
          )}
        </div>

        {/* ── Actions ───────────────────────────────────────── */}
        <div className={styles.actions}>
          <button className={styles.cancelBtn} onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button
            className={styles.saveBtn}
            onClick={handleSave}
            disabled={!canSave}
            aria-busy={saving}
          >
            {saving ? (
              <><span className={styles.spinner} aria-hidden="true" /> Saving…</>
            ) : transaction ? 'Save Changes' : 'Save Transaction'}
          </button>
        </div>
      </div>
    </div>

  );
}
