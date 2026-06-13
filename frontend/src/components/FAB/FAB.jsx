import styles from './FAB.module.css';

export default function FAB({ onClick }) {
  return (
    <button
      id="fab-add-transaction"
      className={styles.fab}
      onClick={onClick}
      aria-label="Add transaction"
      title="Add transaction"
    >
      <span className={styles.icon} aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
        </svg>
      </span>
      <span className={styles.label}>Add Transaction</span>
    </button>
  );
}
