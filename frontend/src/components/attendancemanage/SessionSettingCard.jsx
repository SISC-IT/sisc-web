import { useState } from 'react';
import styles from './SessionSettingCard.module.css';

const SessionSettingCard = ({ styles: commonStyles, onAddSession }) => {
  const [sessionTitle, setSessionTitle] = useState('');
  const [hh, setHh] = useState('');
  const [mm, setMm] = useState('');
  const [ss, setSs] = useState('');
  const [availableTimeMm, setAvailableTimeMm] = useState('');

  const isFormValid = (title, hour, minute, second, availableMinute) => {
    if (!title) {
      alert('세션 이름을 입력해주세요.');
      return false;
    }
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

  const handleCreateClick = () => {
    const title = sessionTitle.trim();
    const hour = parseInt(hh, 10);
    const minute = parseInt(mm, 10);
    const second = parseInt(ss, 10);
    const availableMinute = parseInt(availableTimeMm, 10);

    // 유효성 검사
    if (!isFormValid(title, hour, minute, second, availableMinute)) return;

    onAddSession(sessionTitle, {
      hh: hh.padStart(2, '0'),
      mm: mm.padStart(2, '0'),
      ss: ss.padStart(2, '0'),
      availableTimeMm: availableMinute,
    });

    // 입력 창 초기화
    setSessionTitle('');
    setHh('');
    setMm('');
    setSs('');
    setAvailableTimeMm('');
  };

  return (
    <div className={styles.SessionSettingCardContainer}>
      <header className={commonStyles.header}>
        <h1>세션 설정</h1>
      </header>
      <div className={styles.form}>
        <div className={commonStyles.inputGroup}>
          <label htmlFor="sessionTitle" className={commonStyles.label}>
            세션 이름
          </label>
          <input
            type="text"
            id="sessionTitle"
            value={sessionTitle}
            onChange={(e) => setSessionTitle(e.target.value)}
            placeholder="세션 이름을 입력해주세요. (ex. 금융 IT팀)"
          />
        </div>
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
          <label htmlFor="sessionAvailableTime" className={commonStyles.label}>
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
            <button onClick={handleCreateClick}>생성</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SessionSettingCard;
