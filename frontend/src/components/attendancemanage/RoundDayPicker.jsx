import { useEffect, useMemo, useState } from 'react';
import styles from '../VerificationModal.module.css';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { useAttendance } from '../../contexts/AttendanceContext';

const DEFAULT_TIME = '18:00';
const DEFAULT_END_TIME_MINUTES = 60;

const formatDatePart = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const formatTimePart = (date) => {
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${hour}:${minute}`;
};

const RoundDayPicker = () => {
  const { selectedSessionId, sessions, handleAddRounds, closeAddRoundsModal } =
    useAttendance();

  const [selectedDate, setSelectedDate] = useState();
  const [roundName, setRoundName] = useState('');
  const [startTime, setStartTime] = useState(DEFAULT_TIME);
  const [endTime, setEndTime] = useState(DEFAULT_TIME);
  const [locationName, setLocationName] = useState('');

  const selectedSession = useMemo(
    () =>
      (sessions || []).find(
        (session) => String(session.sessionId) === String(selectedSessionId)
      ),
    [sessions, selectedSessionId]
  );

  const allowedMinutes =
    Number(selectedSession?.session?.allowedMinutes || 0) > 0
      ? Number(selectedSession.session.allowedMinutes)
      : 0;

  const today = new Date();

  const calculateEndTime = (baseTime, minutesToAdd, baseDate = new Date()) => {
    if (!baseTime || !minutesToAdd || Number(minutesToAdd) <= 0) {
      return {
        time: '',
        nextDay: false,
        iso: '',
      };
    }

    const [hours, minutes] = baseTime.split(':').map(Number);
    if (Number.isNaN(hours) || Number.isNaN(minutes)) {
      return {
        time: '',
        nextDay: false,
        iso: '',
      };
    }

    const startDateTime = new Date(baseDate);
    startDateTime.setHours(hours, minutes, 0, 0);

    const endDateTime = new Date(startDateTime);
    endDateTime.setMinutes(endDateTime.getMinutes() + Number(minutesToAdd));

    return {
      time: formatTimePart(endDateTime),
      nextDay: endDateTime.getDate() !== startDateTime.getDate(),
      iso: `${formatDatePart(endDateTime)}T${formatTimePart(endDateTime)}:00`,
    };
  };

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

  useEffect(() => {
    if (!startTime) return;
    const calculated = calculateEndTime(startTime, DEFAULT_END_TIME_MINUTES);
    setEndTime(calculated.time);
  }, [startTime]);

  const handleStartTimeChange = (e) => {
    const nextStartTime = e.target.value;
    setStartTime(nextStartTime);

    const calculated = calculateEndTime(nextStartTime, DEFAULT_END_TIME_MINUTES);
    setEndTime(calculated.time);
  };

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

    const startDateTime = new Date(`${roundDate}T${startTime}:00`);
    const endDateTime = new Date(`${roundDate}T${endTime}:00`);

    if (endDateTime <= startDateTime) {
      endDateTime.setDate(endDateTime.getDate() + 1);
    }

    const closeAt = `${formatDatePart(endDateTime)}T${formatTimePart(endDateTime)}:00`;

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
    } catch {
      alert('라운드 생성에 실패했습니다.');
    }
  };

  return (
    <div className={styles.overlay}>
      <div className={`${styles.modal} ${styles.roundAddModal}`}>
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
          className={styles.roundAddDayPicker}
          mode="single"
          disabled={{ before: today }}
          selected={selectedDate}
          onSelect={setSelectedDate}
        />

        <div className={styles.roundAddForm}>
          <div className={styles.roundAddField}>
            <label htmlFor="roundName">회차 이름</label>
            <input
              id="roundName"
              className={styles.roundAddInput}
              type="text"
              placeholder="라운드 이름을 입력해주세요"
              value={roundName}
              onChange={(e) => setRoundName(e.target.value)}
            />
          </div>

          <div className={styles.roundAddTimeRow}>
            <div className={styles.roundAddField}>
              <label htmlFor="startTime">시작 시간</label>
              <input
                id="startTime"
                className={`${styles.roundAddInput} ${styles.roundAddTimeInput}`}
                type="time"
                value={startTime}
                onChange={handleStartTimeChange}
              />
            </div>

            <div className={styles.roundAddField}>
              <label htmlFor="endTime">종료 시간</label>
              <input
                id="endTime"
                className={`${styles.roundAddInput} ${styles.roundAddTimeInput}`}
                type="time"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </div>
          </div>

          <p className={styles.roundAddHint}>
            기본 출석 가능 시간: {allowedMinutes > 0 ? `${allowedMinutes}분` : '설정되지 않음'}
          </p>

          <div className={styles.roundAddField}>
            <label htmlFor="locationName">장소</label>
            <input
              id="locationName"
              className={styles.roundAddInput}
              type="text"
              placeholder="장소 이름을 입력해주세요"
              value={locationName}
              onChange={(e) => setLocationName(e.target.value)}
            />
          </div>
        </div>

        <div className={styles.roundAddActionGroup}>
          <button className={styles.roundAddCancelButton} onClick={closeAddRoundsModal}>
            취소
          </button>
          <button
            className={styles.roundAddSubmitButton}
            onClick={handleComplete}
          >
            출석일자 추가
          </button>
        </div>
      </div>
    </div>
  );
};

export default RoundDayPicker;
