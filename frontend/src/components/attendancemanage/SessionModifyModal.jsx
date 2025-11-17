import { useState } from 'react';
import styles from '../VerificationModal.module.css';

const SessionModifyModal = ({
  styles: commonStyles,
  onClose,
  session,
  onSave,
  onDelete,
}) => {
  // 받은 session 파싱
  const parseTime = (timeStr) => {
    const parts = (timeStr || '00:00:00').split(':');
    return {
      h: parts[0] || '00',
      m: parts[1] || '00',
      s: parts[2] || '00',
    };
  };
  const { h = '00', m = '00', s = '00' } = parseTime(session.defaultStartTime);

  const [hh, setHh] = useState(h);
  const [mm, setMm] = useState(m);
  const [ss, setSs] = useState(s);
  const [defaultAvailableMinutes, setDefaultAvailableMinutes] = useState(
    session.defaultAvailableMinutes
  );

  const isFormValid = (hour, minute, second, defaultAvailableMinutes) => {
    if (isNaN(hour) || hour < 0 || hour > 23) {
      alert('출석 시작 시간(시)은 0-23 사이의 숫자로 입력해주세요.');
      return false;
    }
    if (isNaN(minute) || minute < 0 || minute > 59) {
      alert('출석 시작 시간(분)은 0-59 사이의 숫자로 입력해주세요.');
      return false;
    }
    if (isNaN(second) || second < 0 || second > 59) {
      alert('출석 시작 시간(초)은 0-59 사이의 숫자로 입력해주세요.');
      return false;
    }
    if (
      isNaN(defaultAvailableMinutes) ||
      defaultAvailableMinutes < 0 ||
      defaultAvailableMinutes > 59
    ) {
      alert('출석 가능 시간(분)은 0-59 사이의 숫자로 입력해주세요.');
      return false;
    }
    return true;
  };

  const handleModifyClick = () => {
    const hour = parseInt(hh, 10);
    const minute = parseInt(mm, 10);
    const second = parseInt(ss, 10);
    const availableMinute = parseInt(defaultAvailableMinutes, 10);

    // 유효성 검사
    if (!isFormValid(hour, minute, second, availableMinute)) return;

    // 상위 컴포넌트로 업데이트된 회차 데이터 전달
    onSave({
      id: session.id,
      defaultStartTime: `${hh.padStart(2, '0')}:${mm.padStart(2, '0')}:${ss.padStart(2, '0')}`,
      defaultAvailableMinutes: availableMinute,
    });

    // 모달 닫기
    onClose();
  };
  const handleDeleteClick = () => {
    if (window.confirm('이 세션을 정말로 삭제하시겠습니까?')) {
      onDelete(session.id);
      onClose();
    }
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h1>세션 정보 수정</h1>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
          >
            &times;
          </button>
        </div>

        <div className={styles.form}>
          <div className={commonStyles.inputGroup}>
            <label htmlFor="sessionStartTime" className={commonStyles.label}>
              출석 시작 시간
            </label>
            <div className={styles.timeInputGroup}>
              <input
                type="text"
                id="sessionStartTime"
                value={hh}
                maxLength="2"
                onChange={(e) => setHh(e.target.value)}
                placeholder="시(HH)"
              />
              <input
                type="text"
                value={mm}
                maxLength="2"
                onChange={(e) => setMm(e.target.value)}
                placeholder="분(MM)"
              />
              <input
                type="text"
                value={ss}
                maxLength="2"
                onChange={(e) => setSs(e.target.value)}
                placeholder="초(SS)"
              />
            </div>
          </div>
          <div className={commonStyles.inputGroup}>
            <label
              htmlFor="sessionAvailableTime"
              className={commonStyles.label}
            >
              출석 가능 시간
            </label>
            <div className={styles.availableTimeInputGroup}>
              <input
                type="text"
                id="sessionAvailableTime"
                value={defaultAvailableMinutes}
                maxLength="2"
                onChange={(e) => setDefaultAvailableMinutes(e.target.value)}
                placeholder="분(MM)"
              />
            </div>
            <div className={styles.modifyButtonGroup}>
              <button
                className={`${styles.button} ${styles.resetPasswordButton}`}
                onClick={handleDeleteClick}
              >
                삭제
              </button>
              <button
                className={`${styles.button} ${styles.submitButton}`}
                onClick={handleModifyClick}
              >
                완료
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SessionModifyModal;
