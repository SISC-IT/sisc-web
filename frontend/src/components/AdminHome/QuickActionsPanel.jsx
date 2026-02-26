import { Link } from 'react-router-dom';

const QuickActionsPanel = ({ actions = [], styles }) => {
  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>빠른 작업</h2>
      </div>
      <div className={styles.quickActionWrap}>
        {actions.map((action) => (
          <Link key={action.id} to={action.to} className={styles.quickActionButton}>
            {action.label}
          </Link>
        ))}
      </div>
    </section>
  );
};

export default QuickActionsPanel;
