import styles from './AdminHome.module.css';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import MemberList from '../components/AdminHome/MemberList';
import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
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

      <div className={styles.statsGrid}>
        {data.dashboardStats.map((stat) => (
          <section key={stat.id} className={styles.card}>
            <p className={styles.cardTitle}>{stat.title}</p>
            <p className={styles.cardValue}>{stat.value}</p>
            <p className={styles.cardDescription}>{stat.description}</p>
          </section>
        ))}
      </div>

      <div className={styles.gridTwoColumns}>
        <section className={styles.panel}>
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>가입 승인 대기</h2>
            <Link to="/admin/members/approval" className={styles.linkButton}>
              전체 보기
            </Link>
          </div>
          <ul className={styles.list}>
            {data.pendingApprovals.map((member) => (
              <li key={member.id} className={styles.listItem}>
                <div>
                  <p className={styles.memberName}>{member.name}</p>
                  <p className={styles.memberMeta}>{member.email}</p>
                </div>
                <div className={styles.memberActions}>
                  <span className={styles.badge}>{member.requestedAt}</span>
                  <button type="button" className={styles.actionPrimary}>
                    승인
                  </button>
                  <button type="button" className={styles.actionSecondary}>
                    거절
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>

        <section className={styles.panel}>
          <div className={styles.panelHeader}>
            <h2 className={styles.panelTitle}>최근 활동</h2>
          </div>
          <ul className={styles.list}>
            {data.recentActivities.map((activity) => (
              <li key={activity.id} className={styles.listItemColumn}>
                <p className={styles.activityMessage}>{activity.message}</p>
                <p className={styles.memberMeta}>{activity.time}</p>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className={styles.panel}>
        <div className={styles.panelHeader}>
          <h2 className={styles.panelTitle}>빠른 작업</h2>
        </div>
        <div className={styles.quickActionWrap}>
          {data.quickActions.map((action) => (
            <Link
              key={action.id}
              to={action.to}
              className={styles.quickActionButton}
            >
              {action.label}
            </Link>
          ))}
        </div>
      </section>

          <section className={styles.panel}>
            <div className={styles.panelHeader}>
              <h2 className={styles.panelTitle}>회원 목록</h2>
            </div>
            <MemberList members={data.members} />
          </section>
        </div>
      </div>
    </div>
  );
};

export default AdminHome;
