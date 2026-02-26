import { Link } from 'react-router-dom';

const PendingApprovalsPanel = ({ members = [], styles }) => {
  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>가입 승인 대기</h2>
        <Link to="/admin/members/approval" className={styles.linkButton}>
          전체 보기
        </Link>
      </div>
      <ul className={styles.list}>
        {members.map((member) => (
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
  );
};

export default PendingApprovalsPanel;
