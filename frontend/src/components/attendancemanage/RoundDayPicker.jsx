import { useState, useEffect } from 'react';
import styles from '../VerificationModal.module.css';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { useAttendance } from '../../contexts/AttendanceContext';

const RoundDayPicker = () => {
  const { selectedSessionId, handleAddRounds, closeAddRoundsModal } =
    useAttendance();

  const [selectedDate, setSelectedDate] = useState();
  const [roundName, setRoundName] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [locationName, setLocationName] = useState('');

  const today = new Date();

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        closeAddRoundsModal();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [closeAddRoundsModal]);

  const handleComplete = async () => {
    if (!selectedSessionId) {
      alert('세션을 먼저 선택해주세요.');
      return;
    }

    if (
      !selectedDate ||
      !roundName ||
      !startTime ||
      !endTime ||
      !locationName
    ) {
      alert('모든 항목을 입력해주세요.');
      return;
    }

    // 날짜 문자열 (YYYY-MM-DD)
    const roundDate = selectedDate.toLocaleDateString('sv-SE');

    const startAt = `${roundDate}T${startTime}:00`;
    const closeAt = `${roundDate}T${endTime}:00`;

    const newRound = {
      roundDate,
      startAt,
      closeAt,
      roundName,
      locationName,
    };

    try {
      await handleAddRounds(selectedSessionId, [newRound]);
      console.log('새로운 라운드 데이터:', newRound);
      closeAddRoundsModal();
    } catch (err) {
      alert('라운드 생성에 실패했습니다.');
    }
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h1>회차 추가하기</h1>
          <button
            type="button"
            className={styles.closeButton}
            onClick={closeAddRoundsModal}
          >
            &times;
          </button>
        </div>

        <DayPicker
          mode="single"
          disabled={{ before: today }}
          selected={selectedDate}
          onSelect={setSelectedDate}
        />

        <div style={{ marginTop: '20px' }}>
          <input
            type="text"
            placeholder="라운드 이름"
            value={roundName}
            onChange={(e) => setRoundName(e.target.value)}
          />

          <input
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
          />

          <input
            type="time"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
          />

          <input
            type="text"
            placeholder="장소 이름"
            value={locationName}
            onChange={(e) => setLocationName(e.target.value)}
          />
        </div>

        <hr />
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
