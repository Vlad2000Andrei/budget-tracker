import { useState, useEffect, useCallback } from 'react';
import axiosInstance from '../../api/axiosInstance';
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
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  const loadSummary = useCallback(async () => {
    try {
      const res = await axiosInstance.get('/v1/dashboard-summary');
      setData(res.data);
    } catch (err) {
      console.error('Failed to load dashboard summary', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSummary();
    const handleRefresh = () => loadSummary();
    window.addEventListener('transaction-added', handleRefresh);
    return () => {
      window.removeEventListener('transaction-added', handleRefresh);
    };
  }, [loadSummary]);

  if (loading || !data) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.spinner} aria-hidden="true" />
        <span>Loading financial overview…</span>
      </div>
    );
  }

  const { totalBalance, balanceCurrency, monthIncome, monthExpenses, budgets, savingsGoals } = data;
  const netSavings = monthIncome - monthExpenses;

  return (
    <div className={styles.grid}>
      {/* ── Total Balance ─────────────────────────────── */}
      <div className={`${styles.card} ${styles.balanceCard}`}>
        <span className={styles.cardLabel}>Total Balance</span>
        <span className={styles.balanceAmount}>{fmt(totalBalance, balanceCurrency)}</span>
        <span className={styles.cardSub}>Across all accounts</span>
        <div className={styles.balanceBadge} aria-hidden="true">💳</div>
      </div>

      {/* ── Monthly Cash Flow ─────────────────────────── */}
      <div className={`${styles.card} ${styles.cashFlowCard}`}>
        <span className={styles.cardLabel}>This Month</span>
        <div className={styles.cashFlowRow}>
          <div className={styles.cashItem}>
            <span className={styles.cashIcon} aria-label="Income">↑</span>
            <div>
              <div className={styles.cashLabel}>Income</div>
              <div className={`${styles.cashAmount} ${styles.income}`}>{fmt(monthIncome, balanceCurrency)}</div>
            </div>
          </div>
          <div className={styles.cashDivider} aria-hidden="true" />
          <div className={styles.cashItem}>
            <span className={styles.cashIcon} aria-label="Expenses">↓</span>
            <div>
              <div className={styles.cashLabel}>Expenses</div>
              <div className={`${styles.cashAmount} ${styles.expense}`}>{fmt(monthExpenses, balanceCurrency)}</div>
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
          {budgets.map((b) => (
            <div key={b.id} className={styles.goalRow}>
              <div className={styles.goalMeta}>
                <span className={styles.goalName}>
                  {getCategoryIcon(b.categoryIcon)} {b.categoryName}
                </span>
                <span className={`${styles.goalPct} ${b.pct >= 90 ? styles.goalPctDanger : ''}`}>{b.pct}%</span>
              </div>
              <ProgressBar pct={b.pct} variant="primary" />
              <span className={styles.goalAmounts}>{fmt(b.spent, balanceCurrency)} / {fmt(b.limit, balanceCurrency)}</span>
            </div>
          ))}
        </div>

        <div className={styles.goalSection}>
          <span className={styles.goalSectionTitle}>Savings</span>
          {savingsGoals.map((g) => (
            <div key={g.id} className={styles.goalRow}>
              <div className={styles.goalMeta}>
                <span className={styles.goalName}>
                  {getCategoryIcon(g.categoryIcon)} {g.categoryName}
                </span>
                <span className={styles.goalPct}>{g.pct}%</span>
              </div>
              <ProgressBar pct={g.pct} variant="tertiary" />
              <span className={styles.goalAmounts}>{fmt(g.current, balanceCurrency)} / {fmt(g.target, balanceCurrency)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
