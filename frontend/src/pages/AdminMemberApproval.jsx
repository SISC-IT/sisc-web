import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import AdminMemberApprovalList from '../components/AdminMemberApproval/AdminMemberApproval';
import styles from './AdminMemberApproval.module.css';

const AdminMemberApproval = () => {
  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="가입 승인" />
        <div className={styles.contentArea}>
          <AdminMemberApprovalList />
        </div>
      </div>
    </div>
  );
};

export default AdminMemberApproval;
