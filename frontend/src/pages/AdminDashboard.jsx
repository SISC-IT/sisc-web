import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import AdminDashbord from '../components/AdminDashbord/AdminDashbord';
import styles from './AdminDashboard.module.css';

const AdminDashboard = () => {
  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="통계 대시보드" />
        <div className={styles.contentArea}>
          <AdminDashbord />
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;
