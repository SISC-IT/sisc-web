import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import AdminMemberManageView from '../components/AdminMemberManage/AdminMemberManage';
import styles from './AdminMemberManage.module.css';

const AdminMemberManage = () => {
  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="회원 관리" />
        <div className={styles.contentArea}>
          <AdminMemberManageView />
        </div>
      </div>
    </div>
  );
};

export default AdminMemberManage;
