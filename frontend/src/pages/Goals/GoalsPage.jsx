import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import axiosInstance from '../../api/axiosInstance';
import styles from './GoalsPage.module.css';
import { getCategoryIcon } from '../../api/utils';

// Helper to format currency
function formatCurrency(amount, currency) {
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

function formatDate(dt) {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  } catch {
    return dt;
  }
}

export default function GoalsPage() {
  const { user } = useAuth();

  // Tabs: 'BUDGETS' | 'SAVINGS'
  const [activeTab, setActiveTab] = useState('BUDGETS');

  // Lists state
  const [budgets, setBudgets] = useState([]);
  const [savingsGoals, setSavingsGoals] = useState([]);
  const [categories, setCategories] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [activeDashboardBudgets, setActiveDashboardBudgets] = useState([]);

  // UI States
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [alert, setAlert] = useState(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingGoal, setEditingGoal] = useState(null);
  const [goalToDelete, setGoalToDelete] = useState(null);
  const [confirmName, setConfirmName] = useState('');

  // Savings Transaction Modal
  const [txGoal, setTxGoal] = useState(null);
  const [txType, setTxType] = useState('DEPOSIT');
  const [txFromAccountId, setTxFromAccountId] = useState('');
  const [txToAccountId, setTxToAccountId] = useState('');
  const [txAmount, setTxAmount] = useState('');
  const [txCurrency, setTxCurrency] = useState('USD');
  const [txDate, setTxDate] = useState(new Date().toISOString().slice(0, 16));
  const [txNotes, setTxNotes] = useState('');
  const [txSaving, setTxSaving] = useState(false);
  const [txAlert, setTxAlert] = useState(null);

  // Transaction History
  const [historyGoalId, setHistoryGoalId] = useState(null);  // goal whose history is expanded
  const [historyMap, setHistoryMap] = useState({});           // goalId -> array of txs
  const [historyLoading, setHistoryLoading] = useState(false);

  // Form Fields (budget / savings goal CRUD)
  const [categoryId, setCategoryId] = useState('');
  const [amount, setAmount] = useState('');
  const [startDate, setStartDate] = useState(new Date().toISOString().split('T')[0]);
  const [endDate, setEndDate] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [rolloverRule, setRolloverRule] = useState('NONE');
  const [goalType, setGoalType] = useState('ONE_OFF');

  // Fetch all necessary data
  const fetchData = useCallback(async () => {
    setLoading(true);
    setAlert(null);
    try {
      const [catsRes, budgetsRes, savingsRes, summaryRes, accountsRes] = await Promise.all([
        axiosInstance.get('/v1/categories'),
        axiosInstance.get('/v1/budgets'),
        axiosInstance.get('/v1/savings-goals'),
        axiosInstance.get('/v1/dashboard-summary').catch(err => {
          console.warn('Dashboard summary fetch failed, using fallback empty values', err);
          return { data: { budgets: [] } };
        }),
        axiosInstance.get('/v1/accounts'),
      ]);

      setCategories(catsRes.data);
      setBudgets(budgetsRes.data);
      setSavingsGoals(savingsRes.data);
      setActiveDashboardBudgets(summaryRes.data?.budgets || []);
      setAccounts(accountsRes.data);
    } catch (err) {
      setAlert({
        type: 'error',
        text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to load page data.',
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Auto-clear success alerts
  useEffect(() => {
    if (alert?.type === 'success') {
      const timer = setTimeout(() => setAlert(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [alert]);

  useEffect(() => {
    if (txAlert?.type === 'success') {
      const timer = setTimeout(() => setTxAlert(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [txAlert]);

  // ─── Goal CRUD ───────────────────────────────────────────────────────────────

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    resetForm();
  };

  const resetForm = () => {
    setEditingGoal(null);
    setCategoryId('');
    setAmount('');
    setStartDate(new Date().toISOString().split('T')[0]);
    setEndDate('');
    setTargetDate('');
    setRolloverRule('NONE');
    setGoalType('ONE_OFF');
    setIsFormOpen(false);
  };

  const startEdit = (goal) => {
    setEditingGoal(goal);
    setCategoryId(goal.categoryId ? goal.categoryId.toString() : 'OVERALL');
    setAlert(null);
    setIsFormOpen(true);

    if (activeTab === 'BUDGETS') {
      setAmount(goal.amountLimit.toString());
      setStartDate(goal.startDate);
      setEndDate(goal.endDate || '');
      setRolloverRule(goal.rolloverRule || 'NONE');
    } else {
      setAmount(goal.targetAmount.toString());
      setGoalType(goal.goalType || 'ONE_OFF');
      setTargetDate(goal.targetDate || '');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!categoryId || !amount) {
      setAlert({ type: 'error', text: 'Please fill in all required fields.' });
      return;
    }

    const numAmount = parseFloat(amount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setAlert({ type: 'error', text: 'Amount must be a positive number greater than 0.' });
      return;
    }

    if (activeTab === 'BUDGETS') {
      if (!startDate) {
        setAlert({ type: 'error', text: 'Start date is required for budgets.' });
        return;
      }
      if (endDate && startDate > endDate) {
        setAlert({ type: 'error', text: 'Start date must be before or equal to End date.' });
        return;
      }
    }

    setSaving(true);
    setAlert(null);

    try {
      const isOverall = categoryId === 'OVERALL';
      const cat = isOverall ? { name: 'Overall Budget' } : categories.find(c => c.id === parseInt(categoryId));
      if (!cat) throw new Error('Selected category not found.');

      if (activeTab === 'BUDGETS') {
        const payload = {
          categoryId: isOverall ? null : parseInt(categoryId),
          amountLimit: numAmount,
          startDate,
          endDate: endDate || null,
          rolloverRule,
        };
        if (editingGoal) {
          await axiosInstance.patch(`/v1/budgets/${editingGoal.id}`, payload);
          setAlert({ type: 'success', text: isOverall ? 'Overall budget updated successfully.' : `Budget for "${cat.name}" updated successfully.` });
        } else {
          await axiosInstance.post('/v1/budgets', payload);
          setAlert({ type: 'success', text: isOverall ? 'Overall budget created successfully.' : `Budget for "${cat.name}" created successfully.` });
        }
      } else {
        if (editingGoal) {
          const payload = { categoryId: parseInt(categoryId), targetAmount: numAmount, goalType, targetDate: targetDate || null };
          await axiosInstance.patch(`/v1/savings-goals/${editingGoal.id}`, payload);
          setAlert({ type: 'success', text: `Savings goal for "${cat.name}" updated successfully.` });
        } else {
          const payload = { categoryId: parseInt(categoryId), targetAmount: numAmount, goalType, targetDate: targetDate || null };
          await axiosInstance.post('/v1/savings-goals', payload);
          setAlert({ type: 'success', text: `Savings goal for "${cat.name}" created successfully.` });
        }
      }

      resetForm();
      fetchData();
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      setAlert({
        type: 'error',
        text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to save goal.',
      });
    } finally {
      setSaving(false);
    }
  };

  const promptDelete = (goal) => {
    setGoalToDelete(goal);
    setConfirmName('');
  };

  const confirmDeleteGoal = async () => {
    if (!goalToDelete) return;
    const cat = goalToDelete.categoryId ? categories.find(c => c.id === goalToDelete.categoryId) : { name: 'Overall Budget' };
    setAlert(null);

    try {
      if (activeTab === 'BUDGETS') {
        await axiosInstance.delete(`/v1/budgets/${goalToDelete.id}`);
      } else {
        await axiosInstance.delete(`/v1/savings-goals/${goalToDelete.id}`);
      }

      setAlert({ type: 'success', text: `Successfully deleted target for "${cat?.name || 'Category'}".` });
      setGoalToDelete(null);
      setConfirmName('');

      if (editingGoal?.id === goalToDelete.id) resetForm();
      fetchData();
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      setAlert({
        type: 'error',
        text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to delete goal.',
      });
      setGoalToDelete(null);
    }
  };

  // ─── Savings Transaction Modal ───────────────────────────────────────────────

  const openTxModal = (goal, type = 'DEPOSIT') => {
    setTxGoal(goal);
    setTxType(type);
    // Pre-select sensible defaults: first non-savings as FROM for deposit, first savings-type as FROM for withdrawal
    const firstAccount = accounts[0];
    const secondAccount = accounts[1];
    if (type === 'DEPOSIT') {
      setTxFromAccountId(firstAccount ? firstAccount.id.toString() : '');
      setTxToAccountId(secondAccount ? secondAccount.id.toString() : (firstAccount ? firstAccount.id.toString() : ''));
      setTxCurrency(firstAccount ? firstAccount.currency : (user?.defaultCurrency || 'USD'));
    } else {
      setTxFromAccountId(secondAccount ? secondAccount.id.toString() : (firstAccount ? firstAccount.id.toString() : ''));
      setTxToAccountId(firstAccount ? firstAccount.id.toString() : '');
      setTxCurrency(secondAccount ? secondAccount.currency : (firstAccount ? firstAccount.currency : (user?.defaultCurrency || 'USD')));
    }
    setTxAmount('');
    setTxDate(new Date().toISOString().slice(0, 16));
    setTxNotes('');
    setTxAlert(null);
  };

  const closeTxModal = () => {
    setTxGoal(null);
    setTxAlert(null);
  };

  // When type toggle changes, swap FROM/TO accounts to match the new direction
  const handleTxTypeChange = (newType) => {
    setTxType(newType);
    // Swap accounts to reflect the reversed direction
    setTxFromAccountId(txToAccountId);
    setTxToAccountId(txFromAccountId);
    // Update currency to match new from-account
    const newFromAcc = accounts.find(a => a.id.toString() === txToAccountId);
    if (newFromAcc) setTxCurrency(newFromAcc.currency);
  };

  const handleTxFromAccountChange = (e) => {
    const accId = e.target.value;
    setTxFromAccountId(accId);
    const acc = accounts.find(a => a.id.toString() === accId);
    if (acc) setTxCurrency(acc.currency);
  };

  const handleTxToAccountChange = (e) => {
    setTxToAccountId(e.target.value);
  };

  const handleTxSubmit = async (e) => {
    e.preventDefault();
    if (!txFromAccountId || !txToAccountId || !txAmount) {
      setTxAlert({ type: 'error', text: 'Please fill in all required fields.' });
      return;
    }
    if (txFromAccountId === txToAccountId) {
      setTxAlert({ type: 'error', text: 'Source and destination accounts must be different.' });
      return;
    }
    const numAmount = parseFloat(txAmount);
    if (isNaN(numAmount) || numAmount <= 0) {
      setTxAlert({ type: 'error', text: 'Amount must be a positive number greater than 0.' });
      return;
    }

    setTxSaving(true);
    setTxAlert(null);
    try {
      await axiosInstance.post(`/v1/savings-goals/${txGoal.id}/transactions`, {
        fromAccountId: parseInt(txFromAccountId),
        toAccountId: parseInt(txToAccountId),
        amount: numAmount,
        currency: txCurrency,
        type: txType,
        date: new Date(txDate).toISOString().replace('Z', ''),
        notes: txNotes || null,
      });

      setTxAlert({
        type: 'success',
        text: `${txType === 'DEPOSIT' ? 'Deposit' : 'Withdrawal'} of ${formatCurrency(numAmount, txCurrency)} recorded.`,
      });

      fetchData();
      if (historyGoalId === txGoal.id) {
        fetchHistory(txGoal.id);
      }
      window.dispatchEvent(new Event('transaction-added'));
      setTxAmount('');
      setTxNotes('');
    } catch (err) {
      setTxAlert({
        type: 'error',
        text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to record transaction.',
      });
    } finally {
      setTxSaving(false);
    }
  };

  // ─── Transaction History ─────────────────────────────────────────────────────

  const fetchHistory = useCallback(async (goalId) => {
    setHistoryLoading(true);
    try {
      const res = await axiosInstance.get(`/v1/savings-goals/${goalId}/transactions`);
      setHistoryMap(prev => ({ ...prev, [goalId]: res.data }));
    } catch (err) {
      console.warn('Failed to fetch goal transaction history', err);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const toggleHistory = (goalId) => {
    if (historyGoalId === goalId) {
      setHistoryGoalId(null);
    } else {
      setHistoryGoalId(goalId);
      if (!historyMap[goalId]) {
        fetchHistory(goalId);
      }
    }
  };

  const filteredCategories = categories.filter(c =>
    activeTab === 'BUDGETS' ? c.type === 'EXPENSE' : c.type === 'SAVINGS'
  );

  return (
    <div className={styles.container}>
      {/* Page Header */}
      <header className={styles.header}>
        <span className={styles.headerIcon} role="img" aria-label="Target board">🎯</span>
        <div className={styles.headerText}>
          <h1>Goals</h1>
          <p>Plan your expenses using spending budgets and manage savings objectives.</p>
        </div>
        <button
          className={`${styles.btn} ${styles.btnPrimary} ${styles.mobileAddBtn}`}
          onClick={() => { resetForm(); setIsFormOpen(true); }}
          aria-label={activeTab === 'BUDGETS' ? 'Add Budget' : 'Add Savings Goal'}
          title={activeTab === 'BUDGETS' ? 'Add Budget' : 'Add Savings Goal'}
        >
          <span className={styles.mobileAddBtnIcon} aria-hidden="true">+</span>
          <span className={styles.mobileAddBtnLabel}>
            {activeTab === 'BUDGETS' ? 'Add Budget' : 'Add Savings Goal'}
          </span>
        </button>
      </header>

      {/* Main Layout Grid */}
      <div className={styles.goalsLayout}>
        <main className={styles.mainContent}>
          {/* Top Tabs switcher */}
          <div className={styles.tabsContainer}>
            <button
              onClick={() => handleTabChange('BUDGETS')}
              className={`${styles.tabBtn} ${activeTab === 'BUDGETS' ? styles.tabBtnActive : ''}`}
            >
              Spending Budgets
            </button>
            <button
              onClick={() => handleTabChange('SAVINGS')}
              className={`${styles.tabBtn} ${activeTab === 'SAVINGS' ? styles.tabBtnActive : ''}`}
            >
              Savings Goals
            </button>
          </div>

          {/* Alert messages for main view */}
          {alert && !isFormOpen && (
            <div
              className={`${styles.alert} ${alert.type === 'success' ? styles.alertSuccess : styles.alertError}`}
              role="alert"
            >
              <span>{alert.text}</span>
            </div>
          )}

          {/* List display */}
          {loading ? (
            <div className={styles.emptyState}>
              <div className="spinner" aria-hidden="true" />
              <p>Loading goals...</p>
            </div>
          ) : activeTab === 'BUDGETS' ? (
            /* Spending Budgets rendering */
            budgets.length === 0 ? (
              <div className={styles.emptyState}>
                <span className={styles.emptyIcon} role="img" aria-label="Inbox empty">📭</span>
                <p>No budgets set yet. Choose a category and create your first spending limit budget.</p>
              </div>
            ) : (
              <div className={styles.goalsList}>
                {budgets.map((b) => {
                  const cat = b.categoryId
                    ? (categories.find(c => c.id === b.categoryId) || { name: 'Unknown Category', icon: '📦', color: '#FF5733' })
                    : { name: 'Overall Budget', icon: '💰', color: '#4CAF50' };

                  const activeSummary = activeDashboardBudgets.find(db => db.id === b.id);
                  const spent = activeSummary ? activeSummary.spent : 0;
                  const limit = b.amountLimit;
                  const pct = activeSummary ? activeSummary.pct : Math.round((spent / limit) * 100) || 0;
                  const isCloseOrOver = pct >= 90;

                  return (
                    <div key={b.id} className={`${styles.goalCard} ${styles.budgetAccent}`}>
                      <div className={styles.cardHeader}>
                        <div className={styles.categoryInfo}>
                          <span className={styles.colorDot} style={{ backgroundColor: cat.color || '#FF5733' }} />
                          <span className={styles.categoryIcon}>{getCategoryIcon(cat.icon)}</span>
                          <span className={styles.categoryName} title={cat.name}>{cat.name}</span>
                        </div>
                        <span className={`${styles.badge} ${styles.budgetBadge}`}>Budget</span>
                      </div>

                      <div className={styles.amounts}>
                        <div>
                          <span className={styles.currentAmt}>{formatCurrency(spent, user?.defaultCurrency)}</span>
                          <span className={styles.targetAmt}> spent</span>
                        </div>
                        <span className={styles.targetAmt}>Limit: {formatCurrency(limit, user?.defaultCurrency)}</span>
                      </div>

                      <div className={styles.progressContainer}>
                        <div className={styles.progressLabelRow}>
                          <span>Progress</span>
                          <span style={{ fontWeight: 600, color: isCloseOrOver ? 'var(--md-error)' : 'inherit' }}>{pct}%</span>
                        </div>
                        <div className={styles.progressTrack} role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
                          <div
                            className={`${styles.progressFill} ${isCloseOrOver ? styles.progressDanger : styles.progressPrimary}`}
                            style={{ width: `${Math.min(100, pct)}%` }}
                          />
                        </div>
                      </div>

                      <div className={styles.goalDates}>
                        <span>{b.endDate ? `${b.startDate} to ${b.endDate}` : `${b.startDate} (Repeats monthly)`}</span>
                        <div className={styles.actions}>
                          <button className={styles.actionBtn} onClick={() => startEdit(b)} title="Edit Budget" aria-label={`Edit budget for ${cat.name}`}>✏️</button>
                          <button className={`${styles.actionBtn} ${styles.actionBtnDanger}`} onClick={() => promptDelete(b)} title="Delete Budget" aria-label={`Delete budget for ${cat.name}`}>🗑️</button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )
          ) : (
            /* Savings Goals rendering */
            savingsGoals.length === 0 ? (
              <div className={styles.emptyState}>
                <span className={styles.emptyIcon} role="img" aria-label="Inbox empty">📭</span>
                <p>No savings goals set yet. Seed standard amounts to track your savings progress.</p>
              </div>
            ) : (
              <div className={styles.goalsList}>
                {savingsGoals.map((g) => {
                  const cat = categories.find(c => c.id === g.categoryId) || {};
                  const current = g.currentAmount || 0;
                  const target = g.targetAmount;
                  const pct = Math.round((current / target) * 100) || 0;
                  const isHistoryOpen = historyGoalId === g.id;
                  const history = historyMap[g.id] || [];

                  return (
                    <div key={g.id} className={`${styles.goalCard} ${styles.savingsAccent}`}>
                      <div className={styles.cardHeader}>
                        <div className={styles.categoryInfo}>
                          <span className={styles.colorDot} style={{ backgroundColor: cat.color || '#2A9D8F' }} />
                          <span className={styles.categoryIcon}>{getCategoryIcon(cat.icon)}</span>
                          <span className={styles.categoryName} title={cat.name}>{cat.name}</span>
                        </div>
                        <span className={`${styles.badge} ${styles.savingsBadge}`}>
                          {g.goalType === 'MONTHLY' ? 'Monthly' : 'One-off'}
                        </span>
                      </div>

                      <div className={styles.amounts}>
                        <div>
                          <span className={styles.currentAmt}>{formatCurrency(current, user?.defaultCurrency)}</span>
                          <span className={styles.targetAmt}> saved</span>
                        </div>
                        <span className={styles.targetAmt}>Target: {formatCurrency(target, user?.defaultCurrency)}</span>
                      </div>

                      <div className={styles.progressContainer}>
                        <div className={styles.progressLabelRow}>
                          <span>Progress</span>
                          <span style={{ fontWeight: 600 }}>{pct}%</span>
                        </div>
                        <div className={styles.progressTrack} role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
                          <div
                            className={`${styles.progressFill} ${styles.progressTertiary}`}
                            style={{ width: `${Math.min(100, pct)}%` }}
                          />
                        </div>
                      </div>

                      {/* Deposit / Withdraw quick actions */}
                      <div className={styles.savingsActions}>
                        <button
                          id={`deposit-btn-${g.id}`}
                          className={`${styles.txBtn} ${styles.txBtnDeposit}`}
                          onClick={() => openTxModal(g, 'DEPOSIT')}
                          title="Deposit funds into this goal"
                          aria-label={`Deposit into ${cat.name}`}
                        >
                          ↑ Deposit
                        </button>
                        <button
                          id={`withdraw-btn-${g.id}`}
                          className={`${styles.txBtn} ${styles.txBtnWithdraw}`}
                          onClick={() => openTxModal(g, 'WITHDRAWAL')}
                          title="Withdraw funds from this goal"
                          aria-label={`Withdraw from ${cat.name}`}
                          disabled={current <= 0}
                        >
                          ↓ Withdraw
                        </button>
                        <button
                          id={`history-btn-${g.id}`}
                          className={`${styles.txBtn} ${styles.txBtnHistory} ${isHistoryOpen ? styles.txBtnHistoryActive : ''}`}
                          onClick={() => toggleHistory(g.id)}
                          title={isHistoryOpen ? 'Hide history' : 'Show history'}
                          aria-label={`Toggle transaction history for ${cat.name}`}
                          aria-expanded={isHistoryOpen}
                        >
                          {isHistoryOpen ? '▲ History' : '▼ History'}
                        </button>
                      </div>

                      {/* Transaction history panel */}
                      {isHistoryOpen && (
                        <div className={styles.historyPanel}>
                          {historyLoading && !historyMap[g.id] ? (
                            <p className={styles.historyLoading}>Loading history...</p>
                          ) : history.length === 0 ? (
                            <p className={styles.historyEmpty}>No transactions yet. Use Deposit to add funds.</p>
                          ) : (
                            <ul className={styles.historyList} aria-label="Transaction history">
                              {history.map(tx => (
                                <li key={tx.id} className={styles.historyItem}>
                                  <span className={`${styles.historyTypeBadge} ${tx.type === 'DEPOSIT' ? styles.historyDeposit : styles.historyWithdraw}`}>
                                    {tx.type === 'DEPOSIT' ? '↑' : '↓'}
                                  </span>
                                  <div className={styles.historyDetails}>
                                    <span className={styles.historyAmt}>
                                      {tx.type === 'WITHDRAWAL' ? '−' : '+'}{formatCurrency(tx.amount, tx.currency)}
                                    </span>
                                    <span className={styles.historyMeta}>
                                      {tx.fromAccountName}{tx.toAccountName ? ` → ${tx.toAccountName}` : ''} · {formatDate(tx.date)}
                                    </span>
                                    {tx.notes && <span className={styles.historyNotes}>{tx.notes}</span>}
                                  </div>
                                </li>
                              ))}
                            </ul>
                          )}
                        </div>
                      )}

                      <div className={styles.goalDates}>
                        <span>Deadline: {g.targetDate || 'None'}</span>
                        <div className={styles.actions}>
                          <button className={styles.actionBtn} onClick={() => startEdit(g)} title="Edit Savings Goal" aria-label={`Edit savings goal for ${cat.name}`}>✏️</button>
                          <button className={`${styles.actionBtn} ${styles.actionBtnDanger}`} onClick={() => promptDelete(g)} title="Delete Savings Goal" aria-label={`Delete savings goal for ${cat.name}`}>🗑️</button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )
          )}
        </main>

        {/* Right Side Form (Side panel on Desktop, Drawer Bottom sheet on Mobile) */}
        <aside className={`${styles.sidebarContainer} ${isFormOpen ? styles.isOpen : ''}`}>
          <div className={styles.backdrop} onClick={resetForm} />
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.mobileHandle} aria-hidden="true" />
            <div className={styles.formHeader}>
              <h2>
                {editingGoal ? 'Edit ' : 'New '}
                {activeTab === 'BUDGETS' ? 'Budget' : 'Savings Goal'}
              </h2>
              <button type="button" className={styles.closeBtn} onClick={resetForm} aria-label="Close form">✕</button>
            </div>

            <form onSubmit={handleSubmit} className={styles.form}>
              {alert && isFormOpen && (
                <div className={`${styles.alert} ${alert.type === 'success' ? styles.alertSuccess : alert.type === 'error' ? styles.alertError : ''}`} role="alert">
                  <span>{alert.text}</span>
                </div>
              )}

              {/* Category selector */}
              <div className={styles.formGroup}>
                <label htmlFor="goal-category" className={styles.label}>Category</label>
                <select
                  id="goal-category"
                  className={styles.select}
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  disabled={!!editingGoal}
                  required
                >
                  <option value="">-- Select Category --</option>
                  {activeTab === 'BUDGETS' && (
                    <option value="OVERALL">💰 Overall Budget (All categories)</option>
                  )}
                  {filteredCategories.map(c => (
                    <option key={c.id} value={c.id}>{getCategoryIcon(c.icon)} {c.name}</option>
                  ))}
                </select>
                {editingGoal && (
                  <span className={styles.helpText}>Goal category cannot be modified after creation.</span>
                )}
              </div>

              {/* Amount input */}
              <div className={styles.formGroup}>
                <label htmlFor="goal-amount" className={styles.label}>
                  {activeTab === 'BUDGETS' ? 'Limit Amount' : 'Target Amount'}
                </label>
                <input
                  id="goal-amount"
                  type="number"
                  step="0.01"
                  min="0.01"
                  placeholder="0.00"
                  className={styles.input}
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  required
                />
              </div>

              {/* Conditional Inputs based on active tab */}
              {activeTab === 'BUDGETS' ? (
                <>
                  <div className={styles.formGroup}>
                    <label htmlFor="budget-start-date" className={styles.label}>Start Date</label>
                    <input id="budget-start-date" type="date" className={styles.input} value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
                  </div>
                  <div className={styles.formGroup}>
                    <label htmlFor="budget-end-date" className={styles.label}>End Date (Optional)</label>
                    <input id="budget-end-date" type="date" className={styles.input} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
                  </div>
                  <div className={styles.formGroup}>
                    <label htmlFor="budget-rollover" className={styles.label}>Rollover Rule</label>
                    <select id="budget-rollover" className={styles.select} value={rolloverRule} onChange={(e) => setRolloverRule(e.target.value)}>
                      <option value="NONE">NONE — Start fresh every period</option>
                      <option value="SURPLUS">SURPLUS — Roll over excess savings</option>
                      <option value="DEFICIT">DEFICIT — Roll over debt/deficits</option>
                      <option value="ALL">ALL — Roll over entire balances</option>
                    </select>
                  </div>
                </>
              ) : (
                <>
                  <div className={styles.formGroup}>
                    <label htmlFor="savings-goal-type" className={styles.label}>Goal Type</label>
                    <select id="savings-goal-type" className={styles.select} value={goalType} onChange={(e) => setGoalType(e.target.value)} required>
                      <option value="ONE_OFF">One-off (Accumulates over time)</option>
                      <option value="MONTHLY">Monthly (Resets each month)</option>
                    </select>
                  </div>
                  <div className={styles.formGroup}>
                    <label htmlFor="savings-target-date" className={styles.label}>Target Date (Optional)</label>
                    <input id="savings-target-date" type="date" className={styles.input} value={targetDate} onChange={(e) => setTargetDate(e.target.value)} />
                  </div>
                </>
              )}

              <div className={styles.formActions}>
                <button type="button" className={`${styles.btn} ${styles.btnOutlinedDanger}`} onClick={resetForm}>Cancel</button>
                <button type="submit" disabled={saving || !categoryId || !amount} className={`${styles.btn} ${styles.btnPrimary}`}>
                  {saving ? 'Saving...' : editingGoal ? 'Save Changes' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </aside>
      </div>

      {/* ── Savings Transaction Modal ── */}
      {txGoal && (
        <div
          className={styles.confirmBackdrop}
          onClick={(e) => { if (e.target === e.currentTarget) closeTxModal(); }}
          role="dialog"
          aria-modal="true"
          aria-label={txType === 'DEPOSIT' ? 'Deposit into savings goal' : 'Withdraw from savings goal'}
        >
          <div className={styles.txModal}>
            {/* Type toggle header */}
            <div className={styles.txModalHeader}>
              <div className={styles.txTypeToggle}>
                <button
                  type="button"
                  id="tx-type-deposit"
                  className={`${styles.txToggleBtn} ${txType === 'DEPOSIT' ? styles.txToggleDeposit : ''}`}
                  onClick={() => handleTxTypeChange('DEPOSIT')}
                >
                  ↑ Deposit
                </button>
                <button
                  type="button"
                  id="tx-type-withdraw"
                  className={`${styles.txToggleBtn} ${txType === 'WITHDRAWAL' ? styles.txToggleWithdraw : ''}`}
                  onClick={() => handleTxTypeChange('WITHDRAWAL')}
                >
                  ↓ Withdraw
                </button>
              </div>
              <button type="button" className={styles.cancelEditBtn} onClick={closeTxModal} aria-label="Close" style={{ fontSize: '18px' }}>✕</button>
            </div>

            {/* Context line */}
            <p className={styles.txModalContext}>
              {txType === 'DEPOSIT' ? 'Move money from an account into' : 'Move money from'}
              {' '}<strong>{categories.find(c => c.id === txGoal.categoryId)?.name || 'this goal'}</strong>
              {txType === 'WITHDRAWAL' ? ' back to an account' : ''}
            </p>

            {txAlert && (
              <div className={`${styles.alert} ${txAlert.type === 'success' ? styles.alertSuccess : styles.alertError}`} role="alert">
                <span>{txAlert.text}</span>
              </div>
            )}

            <form onSubmit={handleTxSubmit} className={styles.form}>
              {/* FROM account */}
              <div className={styles.formGroup}>
                <label htmlFor="tx-from-account" className={styles.label}>
                  From Account <span style={{ color: 'var(--md-error)' }}>*</span>
                </label>
                {accounts.length === 0 ? (
                  <p className={styles.helpText}>⚠️ No accounts found. Please create an account first.</p>
                ) : (
                  <select
                    id="tx-from-account"
                    className={styles.select}
                    value={txFromAccountId}
                    onChange={handleTxFromAccountChange}
                    required
                  >
                    <option value="">-- Select Source Account --</option>
                    {accounts
                      .filter(a => txType === 'DEPOSIT' ? a.type !== 'SAVINGS' : a.type === 'SAVINGS')
                      .map(a => (
                        <option key={a.id} value={a.id}>
                          {a.name} ({a.currency} — Balance: {formatCurrency(a.balance, a.currency)})
                        </option>
                      ))}
                  </select>
                )}
              </div>

              {/* To Account */}
              <div className={styles.formGroup}>
                <label htmlFor="tx-to-account" className={styles.label}>
                  To Account <span style={{ color: 'var(--md-error)' }}>*</span>
                </label>
                {accounts.length < 2 ? (
                  <p className={styles.helpText}>⚠️ You need at least 2 accounts to perform a cash move.</p>
                ) : (
                  <select
                    id="tx-to-account"
                    className={styles.select}
                    value={txToAccountId}
                    onChange={handleTxToAccountChange}
                    required
                  >
                    <option value="">-- Select Destination Account --</option>
                    {accounts
                      .filter(a => a.id.toString() !== txFromAccountId)
                      .filter(a => txType === 'DEPOSIT' ? a.type === 'SAVINGS' : a.type !== 'SAVINGS')
                      .map(a => (
                        <option key={a.id} value={a.id}>
                          {a.name} ({a.currency} — Balance: {formatCurrency(a.balance, a.currency)})
                        </option>
                      ))}
                  </select>
                )}
              </div>

              {/* Amount + Currency */}
              <div className={styles.formRow}>
                <div className={styles.formGroup} style={{ flex: 2 }}>
                  <label htmlFor="tx-amount" className={styles.label}>Amount</label>
                  <input
                    id="tx-amount"
                    type="number"
                    step="0.01"
                    min="0.01"
                    placeholder="0.00"
                    className={styles.input}
                    value={txAmount}
                    onChange={(e) => setTxAmount(e.target.value)}
                    required
                  />
                </div>
                <div className={styles.formGroup} style={{ flex: 1 }}>
                  <label htmlFor="tx-currency" className={styles.label}>Currency</label>
                  <input
                    id="tx-currency"
                    type="text"
                    maxLength={3}
                    className={styles.input}
                    value={txCurrency}
                    onChange={(e) => setTxCurrency(e.target.value.toUpperCase())}
                    required
                  />
                </div>
              </div>

              {/* Date */}
              <div className={styles.formGroup}>
                <label htmlFor="tx-date" className={styles.label}>Date</label>
                <input
                  id="tx-date"
                  type="datetime-local"
                  className={styles.input}
                  value={txDate}
                  onChange={(e) => setTxDate(e.target.value)}
                  required
                />
              </div>

              {/* Notes */}
              <div className={styles.formGroup}>
                <label htmlFor="tx-notes" className={styles.label}>Notes (Optional)</label>
                <input
                  id="tx-notes"
                  type="text"
                  placeholder="e.g. Monthly savings transfer"
                  className={styles.input}
                  value={txNotes}
                  onChange={(e) => setTxNotes(e.target.value)}
                />
              </div>

              <div className={styles.formActions}>
                <button type="button" className={`${styles.btn} ${styles.btnOutlinedDanger}`} onClick={closeTxModal}>Cancel</button>
                <button
                  type="submit"
                  disabled={txSaving || !txFromAccountId || !txToAccountId || !txAmount || txFromAccountId === txToAccountId || accounts.length < 2}
                  className={`${styles.btn} ${txType === 'DEPOSIT' ? styles.btnDeposit : styles.btnWithdraw}`}
                >
                  {txSaving ? 'Processing...' : txType === 'DEPOSIT' ? '↑ Confirm Deposit' : '↓ Confirm Withdrawal'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Custom Deletion Confirmation Bottom-Sheet Modal ── */}
      {goalToDelete && (
        <div
          className={styles.confirmBackdrop}
          onClick={(e) => { if (e.target === e.currentTarget) setGoalToDelete(null); }}
          role="dialog"
          aria-modal="true"
          aria-label="Confirm goal deletion"
        >
          <div className={styles.confirmToast}>
            <div className={styles.confirmHeader}>
              <h3 className={styles.confirmTitle}>Delete Goal</h3>
              <button className={styles.cancelEditBtn} onClick={() => setGoalToDelete(null)} aria-label="Cancel" style={{ fontSize: '16px' }}>✕</button>
            </div>

            <div className={styles.confirmBody}>
              <div className={styles.confirmWarning}>
                ⚠️ Warning: Deleting the {activeTab === 'BUDGETS' ? 'spending budget' : 'savings goal'} for &quot;
                {goalToDelete.categoryId ? (categories.find(c => c.id === goalToDelete.categoryId)?.name || 'Category') : 'Overall Budget'}&quot; is permanent and cannot be undone.
              </div>

              <div className={styles.confirmForm}>
                <div className={styles.formGroup}>
                  <label htmlFor="confirm-goal-name" className={styles.label}>
                    To confirm, type the category name: <strong>
                      {goalToDelete.categoryId ? (categories.find(c => c.id === goalToDelete.categoryId)?.name || 'Category') : 'Overall Budget'}
                    </strong>
                  </label>
                  <input
                    id="confirm-goal-name"
                    type="text"
                    className={styles.input}
                    placeholder="Type category name..."
                    value={confirmName}
                    onChange={(e) => setConfirmName(e.target.value)}
                    required
                  />
                </div>
              </div>
            </div>

            <div className={styles.confirmActions}>
              <button className={styles.cancelEditBtn} onClick={() => setGoalToDelete(null)} style={{ padding: '8px 16px', fontSize: '14px' }}>Cancel</button>
              <button
                className={`${styles.btn} ${styles.btnDanger}`}
                onClick={confirmDeleteGoal}
                disabled={confirmName !== (goalToDelete.categoryId ? (categories.find(c => c.id === goalToDelete.categoryId)?.name || 'Category') : 'Overall Budget')}
              >
                Verify &amp; Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
