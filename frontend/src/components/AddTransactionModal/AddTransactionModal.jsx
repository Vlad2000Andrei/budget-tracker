import { useEffect, useRef, useState, useMemo } from 'react';
import axiosInstance from '../../api/axiosInstance';
import { useAuth } from '../../context/AuthContext';
import { getCategoryIcon } from '../../api/utils';
import styles from './AddTransactionModal.module.css';

const FREQ_OPTIONS = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'];
const FREQ_LABELS = { DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly' };

const today = () => new Date().toISOString().slice(0, 10);

export default function AddTransactionModal({ onClose }) {
  const { user } = useAuth();
  const [form, setForm] = useState({
    type: 'EXPENSE',
    amount: '',
    currency: user?.defaultCurrency || 'USD',
    exchangeRate: '',
    categoryId: null,
    date: today(),
    accountId: '',
    notes: '',
    makeRecurring: false,
    frequency: 'MONTHLY',
    interval: 1,
    endDate: '',
  });

  const [dbCategories, setDbCategories] = useState([]);
  const [dbAccounts, setDbAccounts] = useState([]);
  const [categorySearch, setCategorySearch] = useState('');
  const [notesExpanded, setNotesExpanded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const amountRef = useRef(null);
  const backdropRef = useRef(null);

  // Fetch categories and accounts on mount
  useEffect(() => {
    async function loadData() {
      try {
        const [catsRes, accountsRes] = await Promise.all([
          axiosInstance.get('/v1/categories'),
          axiosInstance.get('/v1/accounts')
        ]);
        setDbCategories(catsRes.data);
        setDbAccounts(accountsRes.data);
      } catch (err) {
        console.error("Failed to load categories/accounts in modal", err);
      }
    }
    loadData();
  }, []);

  // Update default currency when user info becomes available
  useEffect(() => {
    if (user?.defaultCurrency) {
      setForm(f => ({ ...f, currency: user.defaultCurrency }));
    }
  }, [user]);

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

  // Dynamically map accounts (stored as uppercase ACCOUNTS to match select element loop reference)
  const ACCOUNTS = useMemo(() => {
    return dbAccounts.map(a => ({
      id: a.id,
      name: a.name,
      type: a.type,
    }));
  }, [dbAccounts]);

  const categories = CATEGORIES_BY_TYPE[form.type] ?? [];
  const recentChips = categories.slice(0, 6);
  const searchResults = categorySearch.length >= 2
    ? categories.filter((c) =>
        c.name.toLowerCase().includes(categorySearch.toLowerCase()) ||
        (c.parentName ?? '').toLowerCase().includes(categorySearch.toLowerCase())
      )
    : [];

  const selectedCategory = categories.find((c) => c.id === form.categoryId);
  const showExchangeRate = form.currency !== (user?.defaultCurrency || 'USD');

  async function handleSave() {
    if (!form.amount || !form.categoryId) return;

    setSaving(true);
    setSaveError(null);

    const payload = {
      categoryId: form.categoryId,
      accountId: form.accountId ? parseInt(form.accountId, 10) : undefined,
      amount: parseFloat(form.amount),
      currency: form.currency,
      type: form.type,
      notes: form.notes || undefined,
      date: new Date(form.date).toISOString(),
    };

    if (form.makeRecurring) {
      payload.recurrenceRule = {
        frequency: form.frequency,
        interval: parseInt(form.interval, 10),
        startDate: form.date,
        endDate: form.endDate || undefined,
      };
    }

    try {
      await axiosInstance.post('/v1/transactions', payload);
      window.dispatchEvent(new Event('transaction-added'));
      onClose();
    } catch (err) {
      setSaveError(err.message);
      setSaving(false);
    }
  }

  const canSave = form.amount && form.categoryId && !saving;

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
            <h2 className={styles.sheetTitle}>Add Transaction</h2>
            <button className={styles.closeBtn} onClick={onClose} aria-label="Close">
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
              </svg>
            </button>
          </div>

          {/* ── Type toggle ────────────────────────────────────── */}
          <div className={styles.field}>
            <div className={styles.typeToggle} role="group" aria-label="Transaction type">
              {['EXPENSE', 'INCOME', 'SAVINGS'].map((t) => (
                <button
                  key={t}
                  className={`${styles.typeBtn} ${form.type === t ? styles[`typeBtnActive_${t}`] : ''}`}
                  onClick={() => { set('type', t); set('categoryId', null); setCategorySearch(''); }}
                  aria-pressed={form.type === t}
                >
                  {t === 'EXPENSE' ? '↓ Expense' : t === 'INCOME' ? '↑ Income' : '🏦 Savings'}
                </button>
              ))}
            </div>
          </div>

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
                className={styles.select}
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
                Exchange Rate <span className={styles.labelHint}>(1 {form.currency} = ? USD)</span>
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
          <div className={styles.field}>
            <label className={styles.label}>Category</label>

            {/* Recent chips */}
            <div className={styles.chips} role="group" aria-label="Recent categories">
              {recentChips.map((c) => (
                <button
                  key={c.id}
                  className={`${styles.chip} ${form.categoryId === c.id ? styles.chipActive : ''}`}
                  onClick={() => { set('categoryId', c.id); setCategorySearch(''); }}
                  aria-pressed={form.categoryId === c.id}
                >
                  {c.icon} {c.name}
                </button>
              ))}
            </div>

            {/* Search */}
            <input
              type="search"
              placeholder="Search all categories…"
              className={styles.input}
              value={categorySearch}
              onChange={(e) => setCategorySearch(e.target.value)}
              aria-label="Search categories"
            />

            {/* Search results */}
            {searchResults.length > 0 && (
              <ul className={styles.searchResults} role="listbox" aria-label="Category suggestions">
                {searchResults.map((c) => (
                  <li key={c.id}>
                    <button
                      className={styles.searchResultItem}
                      onClick={() => { set('categoryId', c.id); setCategorySearch(''); }}
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

          {/* ── Date ──────────────────────────────────────────── */}
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

          {/* ── Account (optional) ────────────────────────────── */}
          <div className={styles.field}>
            <label className={styles.label} htmlFor="modal-account">
              Account <span className={styles.labelHint}>optional</span>
            </label>
            <select
              id="modal-account"
              className={styles.select}
              value={form.accountId}
              onChange={(e) => set('accountId', e.target.value)}
            >
              <option value="">— No account —</option>
              {ACCOUNTS.map((a) => (
                <option key={a.id} value={a.id}>{a.name} ({a.type})</option>
              ))}
            </select>
          </div>

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
              onClick={() => set('makeRecurring', !form.makeRecurring)}
              aria-expanded={form.makeRecurring}
            >
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"
                style={{ transform: form.makeRecurring ? 'rotate(90deg)' : 'none', transition: 'transform 200ms' }}>
                <path d="M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
              </svg>
              🔁 Make Recurring <span className={styles.labelHint}>optional</span>
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
                  <label className={styles.label} htmlFor="modal-end-date">
                    End Date <span className={styles.labelHint}>optional — leave blank to repeat forever</span>
                  </label>
                  <input
                    id="modal-end-date"
                    type="date"
                    className={styles.input}
                    value={form.endDate}
                    min={form.date}
                    onChange={(e) => set('endDate', e.target.value)}
                  />
                </div>
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
              ) : 'Save Transaction'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
