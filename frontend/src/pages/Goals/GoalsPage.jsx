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

export default function GoalsPage() {
  const { user } = useAuth();
  
  // Tabs: 'BUDGETS' | 'SAVINGS'
  const [activeTab, setActiveTab] = useState('BUDGETS');

  // Lists state
  const [budgets, setBudgets] = useState([]);
  const [savingsGoals, setSavingsGoals] = useState([]);
  const [categories, setCategories] = useState([]);
  const [activeDashboardBudgets, setActiveDashboardBudgets] = useState([]);

  // UI States
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [alert, setAlert] = useState(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingGoal, setEditingGoal] = useState(null);
  const [goalToDelete, setGoalToDelete] = useState(null);
  const [confirmName, setConfirmName] = useState('');

  // Form Fields
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
      // Parallel fetches for responsiveness
      const [catsRes, budgetsRes, savingsRes, summaryRes] = await Promise.all([
        axiosInstance.get('/v1/categories'),
        axiosInstance.get('/v1/budgets'),
        axiosInstance.get('/v1/savings-goals'),
        axiosInstance.get('/v1/dashboard-summary').catch(err => {
          console.warn('Dashboard summary fetch failed, using fallback empty values', err);
          return { data: { budgets: [] } };
        })
      ]);

      setCategories(catsRes.data);
      setBudgets(budgetsRes.data);
      setSavingsGoals(savingsRes.data);
      setActiveDashboardBudgets(summaryRes.data?.budgets || []);
    } catch (err) {
      setAlert({ 
        type: 'error', 
        text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to load page data.' 
      });
    } finally {
      setLoading(false);
    }
  }, []);

  // Load data on mount
  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Auto-clear success alerts automatically
  useEffect(() => {
    if (alert?.type === 'success') {
      const timer = setTimeout(() => setAlert(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [alert]);

  // Handle tab switch (and reset form)
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

  // Switch to Edit Mode
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

  // Submit form (Create / Update via API)
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
          rolloverRule
        };
        if (editingGoal) {
          // Update Budget
          await axiosInstance.patch(`/v1/budgets/${editingGoal.id}`, payload);
          setAlert({ type: 'success', text: isOverall ? 'Overall budget updated successfully.' : `Budget for "${cat.name}" updated successfully.` });
        } else {
          // Create Budget
          await axiosInstance.post('/v1/budgets', payload);
          setAlert({ type: 'success', text: isOverall ? 'Overall budget created successfully.' : `Budget for "${cat.name}" created successfully.` });
        }
      } else {
        // Savings Goals
        if (editingGoal) {
          // Update Savings Goal
          const payload = {
            categoryId: parseInt(categoryId),
            targetAmount: numAmount,
            goalType,
            targetDate: targetDate || null
          };
          await axiosInstance.patch(`/v1/savings-goals/${editingGoal.id}`, payload);
          setAlert({ type: 'success', text: `Savings goal for "${cat.name}" updated successfully.` });
        } else {
          // Create Savings Goal
          const payload = {
            categoryId: parseInt(categoryId),
            targetAmount: numAmount,
            goalType,
            targetDate: targetDate || null
          };
          await axiosInstance.post('/v1/savings-goals', payload);
          setAlert({ type: 'success', text: `Savings goal for "${cat.name}" created successfully.` });
        }
      }

      resetForm();
      fetchData();
      
      // Dispatch event to sync and refresh other components (e.g. Dashboard Summary cards)
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      setAlert({ 
        type: 'error', 
        text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to save goal.' 
      });
    } finally {
      setSaving(false);
    }
  };

  // Prompt delete confirmation modal
  const promptDelete = (goal) => {
    setGoalToDelete(goal);
    setConfirmName('');
  };

  // Confirm delete via API
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

      if (editingGoal?.id === goalToDelete.id) {
        resetForm();
      }

      fetchData();
      window.dispatchEvent(new Event('transaction-added'));
    } catch (err) {
      setAlert({ 
        type: 'error', 
        text: err.response?.data?.message || err.response?.data?.error || err.message || 'Failed to delete goal.' 
      });
      setGoalToDelete(null);
    }
  };

  // Filter Categories matching current tab type requirement
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
                  
                  // Look up calculated spent details from dashboard summary if active
                  const activeSummary = activeDashboardBudgets.find(db => db.id === b.id);
                  const spent = activeSummary ? activeSummary.spent : 0;
                  const limit = b.amountLimit;
                  const pct = activeSummary ? activeSummary.pct : Math.round((spent / limit) * 100) || 0;
                  const isCloseOrOver = pct >= 90;

                  return (
                    <div key={b.id} className={`${styles.goalCard} ${styles.budgetAccent}`}>
                      <div className={styles.cardHeader}>
                        <div className={styles.categoryInfo}>
                          <span
                            className={styles.colorDot}
                            style={{ backgroundColor: cat.color || '#FF5733' }}
                          />
                          <span className={styles.categoryIcon}>
                            {getCategoryIcon(cat.icon)}
                          </span>
                          <span className={styles.categoryName} title={cat.name}>
                            {cat.name}
                          </span>
                        </div>
                        <span className={`${styles.badge} ${styles.budgetBadge}`}>
                          Budget
                        </span>
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
                          <span style={{ fontWeight: 600, color: isCloseOrOver ? 'var(--md-error)' : 'inherit' }}>
                            {pct}%
                          </span>
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
                          <button
                            className={styles.actionBtn}
                            onClick={() => startEdit(b)}
                            title="Edit Budget"
                            aria-label={`Edit budget for ${cat.name}`}
                          >
                            ✏️
                          </button>
                          <button
                            className={`${styles.actionBtn} ${styles.actionBtnDanger}`}
                            onClick={() => promptDelete(b)}
                            title="Delete Budget"
                            aria-label={`Delete budget for ${cat.name}`}
                          >
                            🗑️
                          </button>
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

                  return (
                    <div key={g.id} className={`${styles.goalCard} ${styles.savingsAccent}`}>
                      <div className={styles.cardHeader}>
                        <div className={styles.categoryInfo}>
                          <span
                            className={styles.colorDot}
                            style={{ backgroundColor: cat.color || '#2A9D8F' }}
                          />
                          <span className={styles.categoryIcon}>
                            {getCategoryIcon(cat.icon)}
                          </span>
                          <span className={styles.categoryName} title={cat.name}>
                            {cat.name}
                          </span>
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
                            className={`${styles.progressFill} ${styles.progressFill} ${styles.progressTertiary}`}
                            style={{ width: `${Math.min(100, pct)}%` }}
                          />
                        </div>
                      </div>

                      <div className={styles.goalDates}>
                        <span>Deadline: {g.targetDate || 'None'}</span>
                        <div className={styles.actions}>
                          <button
                            className={styles.actionBtn}
                            onClick={() => startEdit(g)}
                            title="Edit Savings Goal"
                            aria-label={`Edit savings goal for ${cat.name}`}
                          >
                            ✏️
                          </button>
                          <button
                            className={`${styles.actionBtn} ${styles.actionBtnDanger}`}
                            onClick={() => promptDelete(g)}
                            title="Delete Savings Goal"
                            aria-label={`Delete savings goal for ${cat.name}`}
                          >
                            🗑️
                          </button>
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
              <button
                type="button"
                className={styles.closeBtn}
                onClick={resetForm}
                aria-label="Close form"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSubmit} className={styles.form}>
              {/* Alert block for form-specific errors */}
              {alert && isFormOpen && (
                <div
                  className={`${styles.alert} ${alert.type === 'success' ? styles.alertSuccess : styles.alertError}`}
                  role="alert"
                >
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
                    <option key={c.id} value={c.id}>
                      {getCategoryIcon(c.icon)} {c.name}
                    </option>
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
                  {/* Budget Start & End Date */}
                  <div className={styles.formGroup}>
                    <label htmlFor="budget-start-date" className={styles.label}>Start Date</label>
                    <input
                      id="budget-start-date"
                      type="date"
                      className={styles.input}
                      value={startDate}
                      onChange={(e) => setStartDate(e.target.value)}
                      required
                    />
                  </div>

                  <div className={styles.formGroup}>
                    <label htmlFor="budget-end-date" className={styles.label}>End Date (Optional)</label>
                    <input
                      id="budget-end-date"
                      type="date"
                      className={styles.input}
                      value={endDate}
                      onChange={(e) => setEndDate(e.target.value)}
                    />
                  </div>

                  <div className={styles.formGroup}>
                    <label htmlFor="budget-rollover" className={styles.label}>Rollover Rule</label>
                    <select
                      id="budget-rollover"
                      className={styles.select}
                      value={rolloverRule}
                      onChange={(e) => setRolloverRule(e.target.value)}
                    >
                      <option value="NONE">NONE — Start fresh every period</option>
                      <option value="SURPLUS">SURPLUS — Roll over excess savings</option>
                      <option value="DEFICIT">DEFICIT — Roll over debt/deficits</option>
                      <option value="ALL">ALL — Roll over entire balances</option>
                    </select>
                  </div>
                </>
              ) : (
                <>
                  {/* Goal Type Selector */}
                  <div className={styles.formGroup}>
                    <label htmlFor="savings-goal-type" className={styles.label}>Goal Type</label>
                    <select
                      id="savings-goal-type"
                      className={styles.select}
                      value={goalType}
                      onChange={(e) => setGoalType(e.target.value)}
                      required
                    >
                      <option value="ONE_OFF">One-off (Accumulates over time)</option>
                      <option value="MONTHLY">Monthly (Resets each month)</option>
                    </select>
                  </div>

                  {/* Savings target Date */}
                  <div className={styles.formGroup}>
                    <label htmlFor="savings-target-date" className={styles.label}>Target Date (Optional)</label>
                    <input
                      id="savings-target-date"
                      type="date"
                      className={styles.input}
                      value={targetDate}
                      onChange={(e) => setTargetDate(e.target.value)}
                    />
                  </div>
                </>
              )}

              {/* Form buttons */}
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
                  disabled={saving || !categoryId || !amount}
                  className={`${styles.btn} ${styles.btnPrimary}`}
                >
                  {saving ? 'Saving...' : editingGoal ? 'Save Changes' : 'Create'}
                </button>
              </div>
            </form>
          </div>
        </aside>
      </div>

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
              <button
                className={styles.cancelEditBtn}
                onClick={() => setGoalToDelete(null)}
                aria-label="Cancel"
                style={{ fontSize: '16px' }}
              >
                ✕
              </button>
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
              <button
                className={styles.cancelEditBtn}
                onClick={() => setGoalToDelete(null)}
                style={{ padding: '8px 16px', fontSize: '14px' }}
              >
                Cancel
              </button>
              <button
                className={`${styles.btn} ${styles.btnDanger}`}
                onClick={confirmDeleteGoal}
                disabled={
                  confirmName !== (goalToDelete.categoryId ? (categories.find(c => c.id === goalToDelete.categoryId)?.name || 'Category') : 'Overall Budget')
                }
              >
                Verify & Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
