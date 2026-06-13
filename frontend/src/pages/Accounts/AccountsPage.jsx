import styles from '../StubPage.module.css';

export default function AccountsPage() {
  return (
    <div className={styles.page}>
      <span className={styles.emoji}>🏦</span>
      <h1 className={styles.title}>Accounts</h1>
      <p className={styles.sub}>Manage your checking & savings accounts — coming soon.</p>
    </div>
  );
}
