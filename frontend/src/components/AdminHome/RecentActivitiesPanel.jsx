const RecentActivitiesPanel = ({ activities = [], styles }) => {
  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>최근 활동</h2>
      </div>
      <ul className={styles.list}>
        {activities.map((activity) => (
          <li key={activity.id} className={styles.listItemColumn}>
            <p className={styles.activityMessage}>{activity.message}</p>
            <p className={styles.memberMeta}>{activity.time}</p>
          </li>
        ))}
      </ul>
    </section>
  );
};

export default RecentActivitiesPanel;
