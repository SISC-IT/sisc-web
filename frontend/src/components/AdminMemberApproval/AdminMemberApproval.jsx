import { useEffect, useMemo, useState } from 'react';
import {
  Search,
  CheckCircle,
  XCircle,
  Clock,
  AlertCircle,
  Check,
  X,
} from 'lucide-react';
import styles from './AdminMemberApproval.module.css';
import {
  approvePendingMember,
  approvePendingMembersBulk,
  getAdminMemberManageData,
  rejectPendingMember,
  rejectPendingMembersBulk,
} from '../../utils/adminMemberManageData';

const AdminMemberApprovalList = () => {
  // 검색/선택/모달 제어 상태
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedIds, setSelectedIds] = useState([]);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [actionTarget, setActionTarget] = useState('single');
  const [targetMember, setTargetMember] = useState(null);

  // 데이터 상태
  const [pendingMembers, setPendingMembers] = useState([]);
  const [monthlyApprovedCount, setMonthlyApprovedCount] = useState(0);
  const [monthlyRejectedCount, setMonthlyRejectedCount] = useState(0);

  // 가입 승인 대기 목록/통계 조회
  const loadPendingMembers = async ({ keyword } = {}) => {
    try {
      const data = await getAdminMemberManageData({ keyword });
      setPendingMembers(data.pendingMembers || []);
      setMonthlyApprovedCount(data.monthlyApprovedCount || 0);
      setMonthlyRejectedCount(data.monthlyRejectedCount || 0);
    } catch (error) {
      window.alert(error?.message || '가입 승인 대기 회원을 불러오지 못했습니다.');
      setPendingMembers([]);
    }
  };

  useEffect(() => {
    loadPendingMembers();
  }, []);

  // 검색어 기준 클라이언트 필터링
  const filteredMembers = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();
    if (!normalizedQuery) return pendingMembers;

    return pendingMembers.filter(
      (member) =>
        member.name.toLowerCase().includes(normalizedQuery) ||
        member.email.toLowerCase().includes(normalizedQuery) ||
        member.studentId.includes(normalizedQuery)
    );
  }, [pendingMembers, searchQuery]);

  const toggleSelect = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((value) => value !== id) : [...prev, id]
    );
  };

  // 현재 필터된 목록 전체 선택/해제
  const toggleSelectAll = () => {
    if (selectedIds.length === filteredMembers.length) {
      setSelectedIds([]);
      return;
    }
    setSelectedIds(filteredMembers.map((member) => member.id));
  };

  const handleApprove = (member) => {
    if (member) {
      setTargetMember(member);
      setActionTarget('single');
    } else {
      setActionTarget('bulk');
    }
    setApproveDialogOpen(true);
  };

  const handleReject = (member) => {
    if (member) {
      setTargetMember(member);
      setActionTarget('single');
    } else {
      setActionTarget('bulk');
    }
    setRejectDialogOpen(true);
  };

  // 승인 확정 처리 (단건/일괄)
  const confirmApprove = () => {
    const approveAction = async () => {
      try {
        if (actionTarget === 'single' && targetMember) {
          await approvePendingMember({ userId: targetMember.id });
          setMonthlyApprovedCount((prev) => prev + 1);
        } else {
          await approvePendingMembersBulk({ userIds: selectedIds });
          setMonthlyApprovedCount((prev) => prev + selectedIds.length);
        }

        setApproveDialogOpen(false);
        setSelectedIds([]);
        setTargetMember(null);
        await loadPendingMembers({ keyword: searchQuery.trim() || undefined });
      } catch (error) {
        window.alert(error?.message || '가입 승인 처리에 실패했습니다.');
      }
    };

    approveAction();
  };

  // 거절 확정 처리 (단건/일괄)
  const confirmReject = () => {
    const rejectAction = async () => {
      try {
        if (actionTarget === 'single' && targetMember) {
          await rejectPendingMember({ userId: targetMember.id });
          setMonthlyRejectedCount((prev) => prev + 1);
        } else {
          await rejectPendingMembersBulk({ userIds: selectedIds });
          setMonthlyRejectedCount((prev) => prev + selectedIds.length);
        }

        setRejectDialogOpen(false);
        setSelectedIds([]);
        setTargetMember(null);
        await loadPendingMembers({ keyword: searchQuery.trim() || undefined });
      } catch (error) {
        window.alert(error?.message || '가입 거절 처리에 실패했습니다.');
      }
    };

    rejectAction();
  };

  return (
    <div className={styles.container}>
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={`${styles.iconBox} ${styles.iconWarning}`}>
            <Clock size={20} />
          </div>
          <div>
            <p className={styles.statValue}>{pendingMembers.length}</p>
            <p className={styles.statLabel}>대기 중</p>
          </div>
        </div>

        <div className={styles.statCard}>
          <div className={`${styles.iconBox} ${styles.iconSuccess}`}>
            <CheckCircle size={20} />
          </div>
          <div>
            <p className={styles.statValue}>{monthlyApprovedCount}</p>
            <p className={styles.statLabel}>이번 달 승인</p>
          </div>
        </div>

        <div className={styles.statCard}>
          <div className={`${styles.iconBox} ${styles.iconDanger}`}>
            <XCircle size={20} />
          </div>
          <div>
            <p className={styles.statValue}>{monthlyRejectedCount}</p>
            <p className={styles.statLabel}>이번 달 거절</p>
          </div>
        </div>
      </div>

      <div className={styles.searchRow}>
        <div className={styles.searchWrap}>
          <Search size={16} className={styles.searchIcon} />
          <input
            className={styles.searchInput}
            placeholder="이름, 이메일, 학번으로 검색..."
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
          />
        </div>

        {selectedIds.length > 0 && (
          <div className={styles.bulkActions}>
            <span className={styles.selectedBadge}>{selectedIds.length}명 선택됨</span>
            <button
              type="button"
              className={`${styles.actionButton} ${styles.approveButton}`}
              onClick={() => handleApprove()}
            >
              <Check size={14} /> 일괄 승인
            </button>
            <button
              type="button"
              className={`${styles.actionButton} ${styles.rejectButton}`}
              onClick={() => handleReject()}
            >
              <X size={14} /> 일괄 거절
            </button>
          </div>
        )}
      </div>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th className={styles.checkboxCell}>
                <input
                  type="checkbox"
                  checked={
                    selectedIds.length === filteredMembers.length &&
                    filteredMembers.length > 0
                  }
                  onChange={toggleSelectAll}
                />
              </th>
              <th>신청자</th>
              <th>학번</th>
              <th>학과</th>
              <th>연락처</th>
              <th>신청일시</th>
              <th>가입 메시지</th>
              <th className={styles.rightAlign}>작업</th>
            </tr>
          </thead>
          <tbody>
            {filteredMembers.map((member) => (
              <tr key={member.id}>
                <td className={styles.checkboxCell}>
                  <input
                    type="checkbox"
                    checked={selectedIds.includes(member.id)}
                    onChange={() => toggleSelect(member.id)}
                  />
                </td>
                <td>
                  <div className={styles.memberInfo}>
                    <div className={styles.avatar}>{member.name[0]}</div>
                    <div>
                      <p className={styles.memberName}>{member.name}</p>
                      <p className={styles.memberEmail}>{member.email}</p>
                    </div>
                  </div>
                </td>
                <td>{member.studentId}</td>
                <td>{member.department}</td>
                <td>{member.phoneNumber || '-'}</td>
                <td>
                  <div className={styles.dateBox}>
                    <Clock size={14} />
                    <span>-</span>
                  </div>
                </td>
                <td>
                  {member.message ? (
                    <p className={styles.message} title={member.message}>
                      {member.message}
                    </p>
                  ) : (
                    <span className={styles.emptyText}>-</span>
                  )}
                </td>
                <td className={styles.rightAlign}>
                  <div className={styles.rowActions}>
                    <button
                      type="button"
                      className={`${styles.actionButton} ${styles.approveButton}`}
                      onClick={() => handleApprove(member)}
                    >
                      <Check size={14} /> 승인
                    </button>
                    <button
                      type="button"
                      className={`${styles.actionButton} ${styles.rejectButton}`}
                      onClick={() => handleReject(member)}
                    >
                      <X size={14} /> 거절
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {filteredMembers.length === 0 && (
          <div className={styles.emptyArea}>
            <AlertCircle size={42} />
            <p>대기 중인 가입 신청이 없습니다.</p>
          </div>
        )}
      </div>

      {approveDialogOpen && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalCard}>
            <h3>가입 승인 확인</h3>
            <p>
              {actionTarget === 'single' && targetMember
                ? `${targetMember.name}님의 가입을 승인하시겠습니까?`
                : `${selectedIds.length}명의 가입을 일괄 승인하시겠습니까?`}
            </p>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={styles.modalCancel}
                onClick={() => setApproveDialogOpen(false)}
              >
                취소
              </button>
              <button
                type="button"
                className={styles.modalConfirm}
                onClick={confirmApprove}
              >
                승인
              </button>
            </div>
          </div>
        </div>
      )}

      {rejectDialogOpen && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalCard}>
            <h3>가입 거절 확인</h3>
            <p>
              {actionTarget === 'single' && targetMember
                ? `${targetMember.name}님의 가입을 거절하시겠습니까?`
                : `${selectedIds.length}명의 가입을 일괄 거절하시겠습니까?`}
            </p>
            <div className={styles.modalActions}>
              <button
                type="button"
                className={styles.modalCancel}
                onClick={() => setRejectDialogOpen(false)}
              >
                취소
              </button>
              <button
                type="button"
                className={`${styles.modalConfirm} ${styles.modalReject}`}
                onClick={confirmReject}
              >
                거절
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminMemberApprovalList;
