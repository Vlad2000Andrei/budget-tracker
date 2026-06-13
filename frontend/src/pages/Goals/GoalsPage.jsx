import styles from '../StubPage.module.css';

export default function GoalsPage() {
  return (
    <div className={styles.page}>
      <span className={styles.emoji}>🎯</span>
      <h1 className={styles.title}>Goals</h1>
      <p className={styles.sub}>Spending budgets & savings goals — coming soon.</p>
    </div>
  );
}
