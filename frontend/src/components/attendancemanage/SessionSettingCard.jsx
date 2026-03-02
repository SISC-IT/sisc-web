import { useState } from 'react';
import styles from './SessionSettingCard.module.css';
import { useAttendance } from '../../contexts/AttendanceContext';

const SessionSettingCard = ({ styles: commonStyles }) => {
  const { handleAddSession } = useAttendance();

  const [sessionTitle, setSessionTitle] = useState('');
  const [description, setDescription] = useState('');
  const [allowedMinutes, setAllowedMinutes] = useState('');
  const [status, setStatus] = useState('OPEN');

  const handleCreateClick = async () => {
    const parsedMinutes = parseInt(allowedMinutes, 10);

    if (!sessionTitle.trim()) {
      alert('세션 이름을 입력해주세요.');
      return;
    }

    if (isNaN(parsedMinutes) || parsedMinutes <= 0) {
      alert('출석 가능 시간을 올바르게 입력해주세요.');
      return;
    }

    const requestBody = {
      title: sessionTitle.trim(),
      description: description.trim(),
      allowedMinutes: parsedMinutes,
      status: status,
    };

    try {
      await handleAddSession(requestBody);

      // 초기화
      setSessionTitle('');
      setDescription('');
      setAllowedMinutes('');
      setStatus('OPEN');

      alert('세션이 생성되었습니다.');
    } catch (err) {
      alert('세션 생성 실패');
    }
  };

  return (
    <div className={styles.SessionSettingCardContainer}>
      <header className={commonStyles.header}>
        <h1>세션 설정</h1>
      </header>

      <div className={styles.form}>
        {/* 세션 이름 */}
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

        {/* 세션 설명 */}
        <div className={commonStyles.inputGroup}>
          <label htmlFor="sessionDescription" className={commonStyles.label}>
            세션 설명
          </label>
          <input
            type="text"
            id="sessionDescription"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="세션 설명을 입력해주세요."
          />
        </div>

        {/* 출석 가능 시간 */}
        <div className={commonStyles.inputGroup}>
          <label htmlFor="sessionAvailableTime" className={commonStyles.label}>
            출석 가능 시간 (분)
          </label>
          <div className={styles.availableTimeInputGroup}>
            <input
              type="number"
              id="sessionAvailableTime"
              value={allowedMinutes}
              maxLength="3"
              onChange={(e) => setAllowedMinutes(e.target.value)}
              placeholder="분(MM)"
            />
          </div>
        </div>

        {/* 세션 상태 */}
        <div className={commonStyles.inputGroup}>
          <label htmlFor="sessionStatus" className={commonStyles.label}>
            세션 상태
          </label>
          <select
            id="sessionStatus"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            <option value="OPEN">OPEN</option>
            <option value="CLOSED">CLOSED</option>
          </select>
        </div>

        <div className={commonStyles.buttonGroup}>
          <button onClick={handleCreateClick}>생성</button>
        </div>
      </div>
    </div>
  );
};

export default SessionSettingCard;
