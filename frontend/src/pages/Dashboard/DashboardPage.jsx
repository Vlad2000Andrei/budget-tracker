import { useAuth } from '../../context/AuthContext';
import SummaryCards from '../../components/SummaryCards/SummaryCards';
import TransactionLog from '../../components/TransactionLog/TransactionLog';
import styles from './DashboardPage.module.css';

export default function DashboardPage() {
  const { user } = useAuth();

  const greeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  };

  const firstName = user?.email?.split('@')[0] ?? 'there';

  return (
    <div className={styles.page}>
      {/* Page header */}
      <header className={styles.pageHeader}>
        <div>
          <h1 className={styles.greeting}>{greeting()}, {firstName} 👋</h1>
          <p className={styles.subGreeting}>{"Here's your financial overview."}</p>
        </div>
        <time className={styles.dateStamp} dateTime={new Date().toISOString()}>
          {new Date().toLocaleDateString('en-US', {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric',
          })}
        </time>
      </header>

      {/* Summary cards */}
      <SummaryCards />

      {/* Transaction log */}
      <TransactionLog />
    </div>
  );
}
