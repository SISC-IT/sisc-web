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

const AdminHome = () => {
  const [data, setData] = useState({
    dashboardStats: [],
    pendingApprovals: [],
    recentActivities: [],
    quickActions: [],
    members: [],
  });

  useEffect(() => {
    const loadAdminHomeData = async () => {
      const adminHomeData = await getAdminHomeData();
      setData(adminHomeData);
    };

    loadAdminHomeData();
  }, []);

  return (
    <div className={styles.layout}>
      <AdminSidebar />

      <div className={styles.mainArea}>
        <AdminHeader title="관리자 대시보드" />
        <div className={styles.container}>
          <DashboardStats stats={data.dashboardStats} styles={styles} />

          <div className={styles.gridTwoColumns}>
            <PendingApprovalsPanel members={data.pendingApprovals} styles={styles} />
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
