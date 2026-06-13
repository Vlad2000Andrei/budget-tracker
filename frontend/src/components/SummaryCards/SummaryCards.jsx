import styles from './SummaryCards.module.css';

// Placeholder data — will be replaced with live API data in a follow-up session.
const PLACEHOLDER = {
  totalBalance: 12_450.75,
  balanceCurrency: 'USD',
  monthIncome: 5_200.00,
  monthExpenses: 3_180.50,
  budgets: [
    { label: 'Food & Dining', spent: 420, limit: 600, pct: 70 },
    { label: 'Transport', spent: 95, limit: 150, pct: 63 },
    { label: 'Entertainment', spent: 180, limit: 200, pct: 90 },
  ],
  savingsGoals: [
    { label: 'Emergency Fund', current: 4800, target: 10000, pct: 48 },
    { label: 'Vacation 🏖️', current: 1200, target: 3000, pct: 40 },
  ],
};

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
  const { totalBalance, balanceCurrency, monthIncome, monthExpenses, budgets, savingsGoals } = PLACEHOLDER;
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
              <div className={`${styles.cashAmount} ${styles.income}`}>{fmt(monthIncome)}</div>
            </div>
          </div>
          <div className={styles.cashDivider} aria-hidden="true" />
          <div className={styles.cashItem}>
            <span className={styles.cashIcon} aria-label="Expenses">↓</span>
            <div>
              <div className={styles.cashLabel}>Expenses</div>
              <div className={`${styles.cashAmount} ${styles.expense}`}>{fmt(monthExpenses)}</div>
            </div>
          </div>
        </div>
        <div className={styles.netRow}>
          <span className={styles.netLabel}>Net savings</span>
          <span className={`${styles.netAmount} ${netSavings >= 0 ? styles.positive : styles.negative}`}>
            {netSavings >= 0 ? '+' : ''}{fmt(netSavings)}
          </span>
        </div>
      </div>

      {/* ── Goals Progress ────────────────────────────── */}
      <div className={`${styles.card} ${styles.goalsCard}`}>
        <span className={styles.cardLabel}>Goals at a Glance</span>

        <div className={styles.goalSection}>
          <span className={styles.goalSectionTitle}>Budgets</span>
          {budgets.map((b) => (
            <div key={b.label} className={styles.goalRow}>
              <div className={styles.goalMeta}>
                <span className={styles.goalName}>{b.label}</span>
                <span className={`${styles.goalPct} ${b.pct >= 90 ? styles.goalPctDanger : ''}`}>{b.pct}%</span>
              </div>
              <ProgressBar pct={b.pct} variant="primary" />
              <span className={styles.goalAmounts}>{fmt(b.spent)} / {fmt(b.limit)}</span>
            </div>
          ))}
        </div>

        <div className={styles.goalSection}>
          <span className={styles.goalSectionTitle}>Savings</span>
          {savingsGoals.map((g) => (
            <div key={g.label} className={styles.goalRow}>
              <div className={styles.goalMeta}>
                <span className={styles.goalName}>{g.label}</span>
                <span className={styles.goalPct}>{g.pct}%</span>
              </div>
              <ProgressBar pct={g.pct} variant="tertiary" />
              <span className={styles.goalAmounts}>{fmt(g.current)} / {fmt(g.target)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
