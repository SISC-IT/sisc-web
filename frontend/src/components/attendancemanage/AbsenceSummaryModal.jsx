import React from 'react';
import styles from './AbsenceSummaryModal.module.css';

const AbsenceSummaryModal = ({ isOpen, onClose, userRows }) => {
  if (!isOpen) return null;

  // 결석한 기록이 있는 유저들만 필터링하고 결석 횟수 계산
  const absenceData = (userRows || [])
    .map(user => {
      const totalAbsences = (user.attendances || []).filter(att => att.status === 'ABSENT').length;
      const totalLates = (user.attendances || []).filter(att => att.status === 'LATE').length;
      return {
        ...user,
        totalAbsences,
        totalLates
      };
    })
    .filter(user => user.totalAbsences > 0 || user.totalLates > 0)
    .sort((a, b) => b.totalAbsences - a.totalAbsences || b.totalLates - a.totalLates);

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div
        className={styles.modalContent}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="absence-modal-title"
      >
        <div className={styles.modalHeader}>
          <h2 id="absence-modal-title">결석 및 지각 집계</h2>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
            aria-label="닫기"
          >
            &times;
          </button>
        </div>
        <div className={styles.modalBody}>
          {absenceData.length > 0 ? (
            <table className={styles.summaryTable}>
              <thead>
                <tr>
                  <th>이름</th>
                  <th>학번</th>
                  <th>총 결석</th>
                  <th>총 지각</th>
                </tr>
              </thead>
              <tbody>
                {absenceData.map((user) => (
                  <tr key={user.userId}>
                    <td>{user.userName}</td>
                    <td>{user.studentId}</td>
                    <td className={styles.absentCount}>
                      {user.totalAbsences}회
                    </td>
                    <td className={styles.lateCount}>{user.totalLates}회</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className={styles.noData}>
              결석 또는 지각 기록이 있는 학생이 없습니다.
            </p>
          )}
        </div>
        <div className={styles.modalFooter}>
          <button
            type="button"
            className={styles.confirmButton}
            onClick={onClose}
          >
            확인
          </button>
        </div>
      </div>
    </div>
  );
};

export default AbsenceSummaryModal;
