import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import AdminFeedbackList from '../components/AdminFeedback/AdminFeedbackList';
import styles from './AdminFeedback.module.css';

const AdminFeedback = () => {
  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="피드백 확인" />
        <div className={styles.contentArea}>
          <AdminFeedbackList />
        </div>
      </div>
    </div>
  );
};

export default AdminFeedback;
