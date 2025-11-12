import { useState } from 'react';
import styles from './SessionSettingCard.module.css';

const SessionSettingCard = ({ styles: commonStyles, onAddSession }) => {
  const [sessionTitle, setSessionTitle] = useState('');

  const isFormValid = (title) => {
    if (!title) {
      alert('세션 이름을 입력해주세요.');
      return false;
    }
    return true;
  };

  const handleCreateClick = () => {
    const title = sessionTitle.trim();

    // 유효성 검사
    if (!isFormValid(title)) return;

    console.log('🎯 세션 생성 시작:', title);

    onAddSession(sessionTitle);

    // 입력 창 초기화
    setSessionTitle('');
  };

  return (
    <div className={styles.SessionSettingCardContainer}>
      <header className={commonStyles.header}>
        <h1>세션 생성</h1>
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
            placeholder="세션 이름을 입력해주세요. (ex. 금융 IT팀 정기 모임)"
          />
        </div>
        <div className={commonStyles.inputGroup}>
          <div className={styles.availableTimeInputGroup}>
            <button onClick={handleCreateClick}>세션 생성</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SessionSettingCard;
