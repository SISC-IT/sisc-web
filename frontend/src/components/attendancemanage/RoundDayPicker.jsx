import { useState } from 'react';
import styles from '../VerificationModal.module.css';

import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { v4 as uuid } from 'uuid';
import { useAttendance } from '../../contexts/AttendanceContext';

const RoundDayPicker = () => {
  const { sessions, selectedSessionId, handleAddRounds, closeAddRoundsModal } =
    useAttendance();

  const [selectedRounds, setSelectedRounds] = useState([]);
  const today = new Date();

  const handleComplete = () => {
    const currentSession = sessions.find((s) => s.id === selectedSessionId);

    if (!currentSession) {
      alert('세션을 먼저 선택해주세요.');
      return;
    }
    if (selectedRounds.length === 0) {
      alert('추가할 날짜를 1개 이상 선택해주세요.');
      return;
    }

    const newRounds = selectedRounds.map((date) => {
      const timeZoneOffset = date.getTimezoneOffset() * 60000;
      const dateWithoutOffset = new Date(date.getTime() - timeZoneOffset);
      const dateString = dateWithoutOffset.toISOString().split('T')[0];

      return {
        id: `round-${uuid()}`,
        date: dateString,
        startTime: currentSession.defaultStartTime,
        availableMinutes: currentSession.defaultAvailableMinutes,
        status: 'opened',
        participants: [],
      };
    });

    handleAddRounds(selectedSessionId, newRounds);

    closeAddRoundsModal();
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h1>회차 추가하기</h1>
          <button
            type="button"
            className={styles.closeButton}
            onClick={() => {
              closeAddRoundsModal();
            }}
          >
            &times;
          </button>
        </div>
        <DayPicker
          animate
          mode="multiple"
          disabled={{ before: today }}
          selected={selectedRounds}
          onSelect={setSelectedRounds}
        />
        <hr />
        <p>세션에 추가하고 싶은 날짜를 선택하세요.</p>
        <p>(출석 시작 시간 & 인정 시간은 세션의 디폴트 값으로 설정됨)</p>
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

export default RoundDayPicker;
