import { useEffect } from 'react';
import { useData } from '../../context/DataContext';
import { getCategoryIcon } from '../../api/utils';
import styles from './SummaryCards.module.css';

function fmt(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(amount);
}

function ProgressBar({ pct, variant = 'primary' }) {
  const clamped = Math.min(100, Math.max(0, pct));
  const danger = clamped >= 90;
  return (
    <div className={styles.progressTrack} role="progressbar" aria-valuenow={clamped} aria-valuemin={0} aria-valuemax={100}>
      <div
        className={`${styles.progressFill} ${danger ? styles.progressDanger : styles[`progress_${variant}`]}`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}

export default function SummaryCards() {
  const { dashboardSummary, fetchDashboardSummary, loading } = useData();

  useEffect(() => {
    fetchDashboardSummary();
    const handleRefresh = () => fetchDashboardSummary(true);
    window.addEventListener('transaction-added', handleRefresh);
    return () => {
      window.removeEventListener('transaction-added', handleRefresh);
    };
  }, [fetchDashboardSummary]);

  const isLoading = loading.dashboardSummary || !dashboardSummary;

  if (isLoading) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.spinner} aria-hidden="true" />
        <span>Loading financial overview…</span>
      </div>
    );
  }

  const { totalBalance, balanceCurrency, monthIncome, monthExpenses,
          recurringIncome, oneOffIncome, recurringExpenses, oneOffExpenses,
          budgets, savingsGoals, accounts } = dashboardSummary;
  const netSavings = monthIncome - monthExpenses;

  return (
    <div className={styles.grid}>
      {/* ── Total Balance ─────────────────────────────── */}
      <div className={`${styles.card} ${styles.balanceCard}`}>
        <span className={styles.cardLabel}>Total Balance</span>
        <span className={styles.balanceAmount}>{fmt(totalBalance, balanceCurrency)}</span>
        <span className={styles.cardSub}>Across all accounts</span>
        {accounts && accounts.length > 0 && (
          <div className={styles.accountsProgressList}>
            {accounts.map((acc) => (
              <div key={acc.id} className={styles.accountProgressRow}>
                <div className={styles.accountProgressMeta}>
                  <span className={styles.accountNameText}>
                    {acc.name} <span className={styles.accountPctText}>({acc.percentage}%)</span>
                  </span>
                  <span className={styles.accountAmtText}>{fmt(acc.balance, acc.currency)}</span>
                </div>
                <div className={styles.accountProgressTrack} role="progressbar" aria-valuenow={acc.percentage} aria-valuemin={0} aria-valuemax={100}>
                  <div className={styles.accountProgressFill} style={{ width: `${Math.min(100, Math.max(0, acc.percentage))}%` }} />
                </div>
              </div>
            ))}
          </div>
        )}
        <div className={styles.balanceBadge} aria-hidden="true">💳</div>
      </div>

      {/* ── Monthly Cash Flow ─────────────────────────── */}
      <div className={`${styles.card} ${styles.cashFlowCard}`}>
        <span className={styles.cardLabel}>This Month</span>
        <div className={styles.cashFlowRow}>
          <div className={styles.cashItem}>
            <span className={styles.cashIcon} aria-label="Income">↑</span>
            <div className={styles.cashItemBody}>
              <div className={styles.cashLabel}>Income</div>
              <div className={`${styles.cashAmount} ${styles.income}`}>{fmt(monthIncome, balanceCurrency)}</div>
              <div className={styles.cashBreakdown}>
                <span className={styles.cashBreakdownRow}>
                  <span className={styles.cashBreakdownLabel}>Recurring</span>
                  <span>{fmt(recurringIncome, balanceCurrency)}</span>
                </span>
                <span className={styles.cashBreakdownRow}>
                  <span className={styles.cashBreakdownLabel}>One-off</span>
                  <span>{fmt(oneOffIncome, balanceCurrency)}</span>
                </span>
              </div>
            </div>
          </div>
          <div className={styles.cashDivider} aria-hidden="true" />
          <div className={styles.cashItem}>
            <span className={styles.cashIcon} aria-label="Expenses">↓</span>
            <div className={styles.cashItemBody}>
              <div className={styles.cashLabel}>Expenses</div>
              <div className={`${styles.cashAmount} ${styles.expense}`}>{fmt(monthExpenses, balanceCurrency)}</div>
              <div className={styles.cashBreakdown}>
                <span className={styles.cashBreakdownRow}>
                  <span className={styles.cashBreakdownLabel}>Recurring</span>
                  <span>{fmt(recurringExpenses, balanceCurrency)}</span>
                </span>
                <span className={styles.cashBreakdownRow}>
                  <span className={styles.cashBreakdownLabel}>One-off</span>
                  <span>{fmt(oneOffExpenses, balanceCurrency)}</span>
                </span>
              </div>
            </div>
          </div>
        </div>
        <div className={styles.netRow}>
          <span className={styles.netLabel}>Net savings</span>
          <span className={`${styles.netAmount} ${netSavings >= 0 ? styles.positive : styles.negative}`}>
            {netSavings >= 0 ? '+' : ''}{fmt(netSavings, balanceCurrency)}
          </span>
        </div>
      </div>

      {/* ── Goals Progress ────────────────────────────── */}
      <div className={`${styles.card} ${styles.goalsCard}`}>
        <span className={styles.cardLabel}>Goals at a Glance</span>

        <div className={styles.goalSection}>
          <span className={styles.goalSectionTitle}>Budgets</span>
          {budgets.length === 0 ? (
            <div className={styles.emptyState}>No active budgets</div>
          ) : (
            budgets.map((b) => {
              const remaining = b.limit - b.spent;
              const remainingText = remaining >= 0 ? `${fmt(remaining, balanceCurrency)} remaining` : `${fmt(Math.abs(remaining), balanceCurrency)} over`;
              return (
                <div key={b.id} className={styles.goalRow}>
                  <div className={styles.goalMeta}>
                    <span className={styles.goalName}>
                      {getCategoryIcon(b.categoryIcon)} {b.categoryName}
                    </span>
                    <span className={`${styles.goalPct} ${b.pct >= 90 ? styles.goalPctDanger : ''}`}>{b.pct}%</span>
                  </div>
                  <ProgressBar pct={b.pct} variant="primary" />
                  <div className={styles.goalFooter}>
                    <span className={styles.goalAmounts}>{fmt(b.spent, balanceCurrency)} / {fmt(b.limit, balanceCurrency)}</span>
                    <span className={`${styles.goalRemaining} ${remaining < 0 ? styles.remainingDanger : ''}`}>{remainingText}</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        <div className={styles.goalSection}>
          <span className={styles.goalSectionTitle}>Savings</span>
          {savingsGoals.length === 0 ? (
            <div className={styles.emptyState}>No active savings goals</div>
          ) : (
            savingsGoals.map((g) => {
              const remaining = g.target - g.current;
              const remainingText = remaining > 0 ? `${fmt(remaining, balanceCurrency)} to go` : 'Goal achieved!';
              return (
                <div key={g.id} className={styles.goalRow}>
                  <div className={styles.goalMeta}>
                    <span className={styles.goalName}>
                      {getCategoryIcon(g.categoryIcon)} {g.categoryName}
                    </span>
                    <span className={styles.goalPct}>{g.pct}%</span>
                  </div>
                  <ProgressBar pct={g.pct} variant="tertiary" />
                  <div className={styles.goalFooter}>
                    <span className={styles.goalAmounts}>{fmt(g.current, balanceCurrency)} / {fmt(g.target, balanceCurrency)}</span>
                    <span className={styles.goalRemaining}>{remainingText}</span>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
