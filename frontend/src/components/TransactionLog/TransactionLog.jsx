import { useState } from 'react';
import styles from './TransactionLog.module.css';

// ── Placeholder data ─────────────────────────────────────────────────────────
const TRANSACTIONS = [
  { id: 1, date: '2026-06-13', category: 'Groceries', icon: '🛒', type: 'EXPENSE', amount: -52.40, currency: 'USD', account: 'Primary Checking', notes: 'Weekly grocery run', recurring: false },
  { id: 2, date: '2026-06-13', category: 'Salary', icon: '💼', type: 'INCOME',  amount: 5200.00, currency: 'USD', account: 'Primary Checking', notes: '', recurring: false },
  { id: 3, date: '2026-06-12', category: 'Electricity', icon: '⚡', type: 'EXPENSE', amount: -110.00, currency: 'USD', account: 'Primary Checking', notes: '', recurring: true },
  { id: 4, date: '2026-06-12', category: 'Restaurants', icon: '🍕', type: 'EXPENSE', amount: -34.90, currency: 'USD', account: null, notes: 'Pizza night', recurring: false },
  { id: 5, date: '2026-06-11', category: 'Emergency Fund', icon: '🏦', type: 'SAVINGS', amount: -500.00, currency: 'USD', account: 'Savings Account', notes: '', recurring: true },
  { id: 6, date: '2026-06-10', category: 'Public Transit', icon: '🚌', type: 'EXPENSE', amount: -60.00, currency: 'USD', account: 'Primary Checking', notes: 'Monthly pass', recurring: true },
  { id: 7, date: '2026-06-09', category: 'Freelance', icon: '💻', type: 'INCOME', amount: 800.00, currency: 'USD', account: null, notes: 'Design project', recurring: false },
  { id: 8, date: '2026-06-08', category: 'Streaming', icon: '📺', type: 'EXPENSE', amount: -15.99, currency: 'USD', account: null, notes: '', recurring: true },
];

const RECURRING = [
  { id: 1, category: 'Electricity', icon: '⚡', type: 'EXPENSE', amount: -110.00, currency: 'USD', frequency: 'MONTHLY', nextDate: '2026-07-12' },
  { id: 2, category: 'Emergency Fund', icon: '🏦', type: 'SAVINGS', amount: -500.00, currency: 'USD', frequency: 'MONTHLY', nextDate: '2026-07-11' },
  { id: 3, category: 'Public Transit', icon: '🚌', type: 'EXPENSE', amount: -60.00, currency: 'USD', frequency: 'MONTHLY', nextDate: '2026-07-10' },
  { id: 4, category: 'Streaming', icon: '📺', type: 'EXPENSE', amount: -15.99, currency: 'USD', frequency: 'MONTHLY', nextDate: '2026-07-08' },
];

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

function AllTab() {
  const [typeFilter, setTypeFilter] = useState('All');
  const [search, setSearch] = useState('');

  const filtered = TRANSACTIONS.filter((t) => {
    if (typeFilter !== 'All' && t.type !== typeFilter) return false;
    if (search && !t.category.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  return (
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
        {filtered.map((t) => (
          <div key={t.id} className={styles.row} role="listitem">
            <span className={styles.rowIcon} aria-hidden="true">{t.icon}</span>
            <div className={styles.rowMain}>
              <div className={styles.rowTop}>
                <span className={styles.rowCategory}>
                  {t.category}
                  {t.recurring && <span className={styles.recurTag} title="Recurring"> 🔁</span>}
                </span>
                <span className={`${styles.rowAmount} ${t.amount >= 0 ? styles.positive : styles.negative}`}>
                  {t.amount >= 0 ? '+' : '−'}{fmt(t.amount, t.currency)}
                </span>
              </div>
              <div className={styles.rowBottom}>
                <TypeBadge type={t.type} />
                {t.account && <span className={styles.rowMeta}>{t.account}</span>}
                <span className={styles.rowDate}>{t.date}</span>
                {t.notes && <span className={styles.rowNotes} title={t.notes}>📝</span>}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function RecurringTab() {
  const FREQ_LABEL = { DAILY: 'Daily', WEEKLY: 'Weekly', MONTHLY: 'Monthly', YEARLY: 'Yearly' };

  return (
    <div className={styles.list} role="list">
      {RECURRING.map((r) => (
        <div key={r.id} className={styles.row} role="listitem">
          <span className={styles.rowIcon} aria-hidden="true">{r.icon}</span>
          <div className={styles.rowMain}>
            <div className={styles.rowTop}>
              <span className={styles.rowCategory}>
                🔁 {r.category}
              </span>
              <span className={`${styles.rowAmount} ${r.amount >= 0 ? styles.positive : styles.negative}`}>
                {r.amount >= 0 ? '+' : '−'}{fmt(r.amount, r.currency)}
              </span>
            </div>
            <div className={styles.rowBottom}>
              <TypeBadge type={r.type} />
              <span className={styles.rowMeta}>{FREQ_LABEL[r.frequency]}</span>
              <span className={styles.rowDate}>Next: {r.nextDate}</span>
            </div>
          </div>
          <div className={styles.rowActions}>
            <button className={styles.actionBtn} title="Edit" aria-label={`Edit ${r.category} recurrence`}>
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zm17.71-10.21a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>
            </button>
            <button className={styles.actionBtn} title="Delete" aria-label={`Delete ${r.category} recurrence`}>
              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

export default function TransactionLog() {
  const [tab, setTab] = useState('all');

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
        {tab === 'all' ? <AllTab /> : <RecurringTab />}
      </div>
    </div>
  );
}
