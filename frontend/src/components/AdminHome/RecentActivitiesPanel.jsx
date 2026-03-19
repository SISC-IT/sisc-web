const RecentActivitiesPanel = ({ activities = [], styles }) => {
  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>최근 활동</h2>
      </div>
      <div className={styles.activityList}>
        <ul className={styles.list}>
          {activities.length === 0 ? (
            <li className={styles.listItemColumn}>
              <p className={styles.activityMessage}>최근 활동 로그가 없습니다.</p>
            </li>
          ) : (
            activities.map((activity) => (
              <li key={activity.id} className={styles.listItemColumn}>
                <p className={styles.activityMessage}>{activity.message}</p>
                <p className={styles.memberMeta}>{activity.time}</p>
              </li>
            ))
          )}
        </ul>
      </div>
    </section>
  );
};

export default RecentActivitiesPanel;
