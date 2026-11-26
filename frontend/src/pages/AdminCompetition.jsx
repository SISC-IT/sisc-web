import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import AdminCompetitionView from '../components/AdminCompetition/AdminCompetition';
import styles from './AdminDashboard.module.css';

const AdminCompetition = () => {
  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="수익률 대결 관리" />
        <div className={styles.contentArea}>
          <AdminCompetitionView />
        </div>
      </div>
    </div>
  );
};

export default AdminCompetition;
