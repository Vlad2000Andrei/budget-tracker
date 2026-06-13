import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from '../components/Sidebar/Sidebar';
import FAB from '../components/FAB/FAB';
import AddTransactionModal from '../components/AddTransactionModal/AddTransactionModal';
import styles from './AppLayout.module.css';

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);

  return (
    <div className={styles.shell}>
      <Sidebar
        collapsed={collapsed}
        onCollapse={() => setCollapsed((c) => !c)}
      />

      <main className={styles.main}>
        <Outlet />
      </main>

      <FAB onClick={() => setModalOpen(true)} />

      {modalOpen && (
        <AddTransactionModal onClose={() => setModalOpen(false)} />
      )}
    </div>
  );
}
