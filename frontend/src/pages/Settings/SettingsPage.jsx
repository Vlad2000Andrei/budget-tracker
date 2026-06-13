import styles from '../StubPage.module.css';

export default function SettingsPage() {
  return (
    <div className={styles.page}>
      <span className={styles.emoji}>⚙️</span>
      <h1 className={styles.title}>Settings</h1>
      <p className={styles.sub}>Currency preferences, category manager & profile — coming soon.</p>
    </div>
  );
}
