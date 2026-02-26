import styles from './AdminHome.module.css';
import { useEffect, useState } from 'react';
import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import DashboardStats from '../components/AdminHome/DashboardStats';
import PendingApprovalsPanel from '../components/AdminHome/PendingApprovalsPanel';
import RecentActivitiesPanel from '../components/AdminHome/RecentActivitiesPanel';
import QuickActionsPanel from '../components/AdminHome/QuickActionsPanel';
import MembersPanel from '../components/AdminHome/MembersPanel';
import { getAdminHomeData } from '../utils/adminHomeData';

const initialAdminHomeData = {
  dashboardStats: [],
  pendingApprovals: [],
  recentActivities: [],
  quickActions: [],
  members: [],
};

const AdminHome = () => {
  const [data, setData] = useState(initialAdminHomeData);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadAdminHomeData();
  }, []);

  const loadAdminHomeData = async () => {
    try {
      const adminHomeData = await getAdminHomeData();
      setData(adminHomeData);
      setError(null);
    } catch (loadError) {
      console.error('관리자 홈 데이터 조회 실패:', loadError);
      setError('관리자 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      setData(initialAdminHomeData);
    }
  };

  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="관리자 대시보드" />
        <div className={styles.container}>
          {error && <p>{error}</p>}
          <DashboardStats stats={data.dashboardStats} styles={styles} />

          <div className={styles.gridTwoColumns}>
            <PendingApprovalsPanel
              members={data.pendingApprovals}
              styles={styles}
              onChanged={loadAdminHomeData}
            />
            <RecentActivitiesPanel activities={data.recentActivities} styles={styles} />
          </div>

          <QuickActionsPanel actions={data.quickActions} styles={styles} />
          <MembersPanel members={data.members} styles={styles} />
        </div>
      </div>
    </div>
  );
};

export default AdminHome;
