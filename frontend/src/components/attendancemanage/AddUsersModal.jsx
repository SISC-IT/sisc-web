import { useState, useEffect } from 'react';
import styles from '../VerificationModal.module.css';
import { useAttendance } from '../../contexts/AttendanceContext';
import { getUserList } from '../../utils/attendanceManage';

const AddUsersModal = () => {
  const { selectedSessionId, handleAddUsers, closeAddUsersModal } =
    useAttendance();
  const [users, setUsers] = useState([]);
  const [selectedUserIds, setSelectedUserIds] = useState(new Set());

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        const userList = await getUserList(selectedSessionId);
        setUsers(userList);
      } catch (err) {
        console.error('사용자 목록을 불러오는 데 실패했습니다:', err);
      }
    };
    if (selectedSessionId) {
      fetchUsers();
    }

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') closeAddUsersModal();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [selectedSessionId, closeAddUsersModal]);

  const toggleUser = (userId) => {
    const newSelection = new Set(selectedUserIds);
    if (newSelection.has(userId)) newSelection.delete(userId);
    else newSelection.add(userId);
    setSelectedUserIds(newSelection);
  };

  const toggleAll = () => {
    if (selectedUserIds.size === users.length) setSelectedUserIds(new Set());
    else setSelectedUserIds(new Set(users.map((u) => u.userId)));
  };

  const handleComplete = async () => {
    if (!selectedSessionId) {
      alert('선택된 세션이 없습니다.');
      return;
    }

    if (selectedUserIds.size === 0) {
      alert('추가할 인원을 1명 이상 선택해주세요.');
      return;
    }

    const idsArray = Array.from(selectedUserIds);

    const results = await Promise.allSettled(
      idsArray.map((userId) => handleAddUsers(selectedSessionId, userId))
    );

    const failedCount = results.filter(
      (result) => result.status === 'rejected'
    ).length;

    if (failedCount === 0) {
      closeAddUsersModal();
      return;
    }

    const successCount = idsArray.length - failedCount;
    console.error(
      `유저 추가 부분 실패: 성공 ${successCount}명, 실패 ${failedCount}명`,
      results.filter((result) => result.status === 'rejected')
    );
    alert(`유저 추가 중 ${failedCount}명이 실패했습니다.`);
  };

  return (
    <div className={styles.overlay}>
      <div className={`${styles.modal} ${styles.largeModal}`}>
        <div className={styles.modalHeader}>
          <div className={styles.titleDiv}>
            <h1>세션 유저 추가</h1>
            <span>{selectedUserIds.size}명 선택되었습니다.</span>
          </div>
          <button
            type="button"
            className={styles.closeButton}
            onClick={closeAddUsersModal}
          >
            &times;
          </button>
        </div>
        <div className={styles.modalContent}>
          <div className={styles.tableWrapper}>
            <table className={styles.userTable}>
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      onChange={toggleAll}
                      checked={
                        users.length > 0 &&
                        selectedUserIds.size === users.length
                      }
                    />
                  </th>
                  <th>이름</th>
                  <th>팀</th>
                  <th>학번</th>
                </tr>
              </thead>
              <tbody>
                {users.length > 0 ? (
                  users.map((user) => (
                    <tr
                      key={user.userId}
                      className={
                        selectedUserIds.has(user.userId) ? styles.activeRow : ''
                      }
                      onClick={() => toggleUser(user.userId)}
                    >
                      <td onClick={(e) => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          checked={selectedUserIds.has(user.userId)}
                          onChange={() => toggleUser(user.userId)}
                        />
                      </td>
                      <td>{user.name}</td>
                      <td>{user.teamName}</td>
                      <td>{user.studentId || '-'}</td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan="4" className={styles.noData}>
                      불러올 수 있는 유저가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
        <div className={styles.modifyButtonGroup}>
          <button className={styles.cancelButton} onClick={closeAddUsersModal}>
            취소
          </button>
          <button
            className={`${styles.button} ${styles.submitButton}`}
            onClick={handleComplete}
          >
            추가하기
          </button>
        </div>
      </div>
    </div>
  );
};

export default AddUsersModal;
