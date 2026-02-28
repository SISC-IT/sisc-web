const DashboardStats = ({ stats = [], styles }) => {
  return (
    <div className={styles.statsGrid}>
      {stats.map((stat) => (
        <section key={stat.id} className={styles.card}>
          <p className={styles.cardTitle}>{stat.title}</p>
          <p className={styles.cardValue}>{stat.value}</p>
          <p className={styles.cardDescription}>{stat.description}</p>
        </section>
      ))}
    </div>
  );
};

export default DashboardStats;
