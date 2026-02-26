import { Link } from 'react-router-dom';
import {
  approvePendingMember,
  rejectPendingMember,
} from '../../utils/adminMemberManageData';

const PendingApprovalsPanel = ({ members = [], styles, onChanged }) => {
  const handleApprove = async (member) => {
    if (!window.confirm(`${member.name}님의 가입을 승인하시겠습니까?`)) {
      return;
    }

    try {
      await approvePendingMember({ userId: member.id });
      await onChanged?.();
    } catch (error) {
      window.alert(error?.message || '가입 승인 처리에 실패했습니다.');
    }
  };

  const handleReject = async (member) => {
    if (!window.confirm(`${member.name}님의 가입을 거절하시겠습니까?`)) {
      return;
    }

    try {
      await rejectPendingMember({ userId: member.id });
      await onChanged?.();
    } catch (error) {
      window.alert(error?.message || '가입 거절 처리에 실패했습니다.');
    }
  };

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
              <button
                type="button"
                className={styles.actionPrimary}
                onClick={() => handleApprove(member)}
              >
                승인
              </button>
              <button
                type="button"
                className={styles.actionSecondary}
                onClick={() => handleReject(member)}
              >
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
