import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import AdminExcelUploadView from '../components/AdminExcelUpload/AdminExcelUpload';
import styles from './AdminExcelUpload.module.css';

const AdminExcelUpload = () => {
  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="엑셀로 회원 등록" />
        <div className={styles.contentArea}>
          <AdminExcelUploadView />
        </div>
      </div>
    </div>
  );
};

export default AdminExcelUpload;
