import { useState } from 'react';
import styles from '../VerificationModal.module.css';

const RoundSettingModal = ({
  styles: commonStyles,
  onClose,
  round,
  onSave,
}) => {
  // 받은 round 파싱
  const parseTime = (timeStr) => {
    const parts = (timeStr || '00:00:00').split(':');
    return {
      h: parts[0] || '00',
      m: parts[1] || '00',
      s: parts[2] || '00',
    };
  };
  const [h = '00', m = '00', s = '00'] = parseTime(round.startTime);

  const [hh, setHh] = useState(h);
  const [mm, setMm] = useState(m);
  const [ss, setSs] = useState(s);
  const [availableTimeMm, setAvailableTimeMm] = useState(
    round.availableMinutes
  );

  const isFormValid = (hour, minute, second, availableMinute) => {
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
    if (isNaN(availableMinute) || availableMinute < 0 || availableMinute > 59) {
      alert('출석 가능 시간(분)은 0-59 사이의 숫자로 입력해주세요.');
      return false;
    }
    return true;
  };

  const handleModifyClick = () => {
    const hour = parseInt(hh, 10);
    const minute = parseInt(mm, 10);
    const second = parseInt(ss, 10);
    const availableMinute = parseInt(availableTimeMm, 10);

    // 유효성 검사
    if (!isFormValid(hour, minute, second, availableMinute)) return;

    // 상위 컴포넌트로 업데이트된 회차 데이터 전달
    onSave({
      id: round.id,
      startTime: `${hh.padStart(2, '0')}:${mm.padStart(2, '0')}:${ss.padStart(2, '0')}`,
      availableMinutes: availableMinute,
    });

    // 모달 닫기
    onClose();
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h1>회차 정보 수정</h1>
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
                value={availableTimeMm}
                maxLength="2"
                onChange={(e) => setAvailableTimeMm(e.target.value)}
                placeholder="분(MM)"
              />
              <button onClick={handleModifyClick}>완료</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RoundSettingModal;
