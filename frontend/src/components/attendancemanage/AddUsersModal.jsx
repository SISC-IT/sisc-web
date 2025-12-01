import { useState, useEffect } from 'react';
import styles from '../VerificationModal.module.css';
import { useAttendance } from '../../contexts/AttendanceContext';
import { getUserList } from '../../utils/attendanceManage';

const AddUsersModal = () => {
  const { sessions, selectedSessionId, handleAddUsers, closeAddUsersModal } =
    useAttendance();
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [users, setUsers] = useState([]);

  // ESC 키로 모달 또는 토스트를 닫는 기능
  useEffect(() => {
    // 유저 리스트 가져오기
    const fetchUsers = async () => {
      try {
        const userList = await getUserList();
        setUsers(userList);
      } catch (err) {
        console.error('사용자 목록을 불러오는 데 실패했습니다:', err);
      }
    };
    if (selectedSessionId) {
      fetchUsers();
    }

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        closeAddUsersModal();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [closeAddUsersModal]);

  console.log(selectedUserId);

  const handleComplete = () => {
    const currentSession = sessions.find(
      (s) => s.attendanceSessionId === selectedSessionId
    );

    if (!currentSession) {
      alert('세션을 먼저 선택해주세요.');
      return;
    }
    if (!selectedUserId) {
      alert('추가할 인원를 1명 이상 선택해주세요.');
      return;
    }

    handleAddUsers(selectedSessionId, selectedUserId);
    closeAddUsersModal();
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h1>세션에 유저 추가하기</h1>
          <button
            type="button"
            className={styles.closeButton}
            onClick={() => {
              closeAddUsersModal();
            }}
          >
            &times;
          </button>
        </div>

        <div className={styles.modalContent}>
          <div className={styles.inputGroup}>
            <label htmlFor="userSelect" className={styles.label}>
              유저 선택
            </label>
            <select
              id="userSelect"
              className={styles.selectInput}
              onChange={(e) => setSelectedUserId(e.target.value)}
            >
              <option value="" disabled>
                유저를 선택하세요
              </option>
              {users &&
                users.map((user) => (
                  <option key={user.userId} value={user.userId}>
                    {user.name} ({user.email})
                  </option>
                ))}
            </select>
          </div>
        </div>

        <div className={styles.modifyButtonGroup}>
          <button
            className={`${styles.button} ${styles.submitButton}`}
            onClick={handleComplete}
          >
            추가
          </button>
        </div>
      </div>
    </div>
  );
};

export default AddUsersModal;
