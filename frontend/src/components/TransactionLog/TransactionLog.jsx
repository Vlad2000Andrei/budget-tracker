import { useState, useEffect, useCallback } from 'react';
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

function AllTab({ categoriesMap, accountsMap }) {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState('All');
  const [search, setSearch] = useState('');

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
          const category = categoriesMap[t.categoryId] || { name: 'Unknown', icon: '📦', color: '#888888' };
          const accountName = t.accountId ? accountsMap[t.accountId] : null;
          const isIncome = t.type === 'INCOME';

          return (
            <div key={t.id} className={styles.row} role="listitem">
              <span className={styles.rowIcon} aria-hidden="true">{category.icon}</span>
              <div className={styles.rowMain}>
                <div className={styles.rowTop}>
                  <span className={styles.rowCategory}>
                    {category.name}
                    {t.recurrenceRuleId && <span className={styles.recurTag} title="Recurring"> 🔁</span>}
                  </span>
                  <span className={`${styles.rowAmount} ${isIncome ? styles.positive : styles.negative}`}>
                    {isIncome ? '+' : '−'}{fmt(t.amount, t.currency)}
                  </span>
                </div>
                <div className={styles.rowBottom}>
                  <TypeBadge type={t.type} />
                  {accountName && <span className={styles.rowMeta}>{accountName}</span>}
                  <span className={styles.rowDate}>{t.date.split('T')[0]}</span>
                  {t.notes && <span className={styles.rowNotes} title={t.notes}>📝</span>}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

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
    if (!window.confirm(`Are you sure you want to delete the recurring rule for "${categoryName}"? Past transactions will remain in your history, but no new occurrences will be generated.`)) {
      return;
    }
    try {
      await axiosInstance.delete(`/v1/recurrence-rules/${ruleId}`);
      loadRecurring();
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      alert("Failed to delete recurrence rule: " + err.message);
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

        return (
          <div key={r.id} className={styles.row} role="listitem">
            <span className={styles.rowIcon} aria-hidden="true">{icon}</span>
            <div className={styles.rowMain}>
              <div className={styles.rowTop}>
                <span className={styles.rowCategory}>
                  🔁 {r.categoryName}
                </span>
                <span className={`${styles.rowAmount} ${isIncome ? styles.positive : styles.negative}`}>
                  {isIncome ? '+' : '−'}{fmt(r.amount, r.currency)}
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
