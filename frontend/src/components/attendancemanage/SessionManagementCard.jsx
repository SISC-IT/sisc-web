import styles from './SessionManagementCard.module.css';
import calendarAddIcon from '../../assets/calendar-icon.svg';

import { getAttendanceSessions } from '../../utils/attendanceManage';
import { useEffect, useState } from 'react';
import { toast } from 'react-toastify';

// 날짜 포맷 함수
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${month}/${day}`;
};

const SessionManagementCard = ({ styles: commonStyles }) => {
  const [sessionList, setSessionList] = useState([]); //  세션 목록
  const [selectedSessionId, setSelectedSessionId] = useState(''); //  선택된 세션
  const [currentDisplayedRounds, setCurrentDisplayedRounds] = useState([]); // 나중에 API 연결

  //  최초 렌더: 세션 목록 불러오기
  useEffect(() => {
    const fetchSessions = async () => {
      try {
        const sessions = await getAttendanceSessions();
        setSessionList(sessions || []);
      } catch (e) {
        toast.error('세션 목록을 불러오지 못했습니다.');
        setSessionList([]);
      }
    };
    fetchSessions();
  }, []);

  // 현재 선택된 세션
  const currentSession = sessionList.find(
    (session) => session.attendanceSessionId === selectedSessionId
  );

  // *라운드 조회 API 연결필요
  useEffect(() => {
    if (!selectedSessionId) {
      setCurrentDisplayedRounds([]);
      return;
    }

    // TODO: 추후 getRounds(selectedSessionId) 연결
    setCurrentDisplayedRounds([]); // 임시
  }, [selectedSessionId]);

  return (
    <div className={styles.sessionManagementCardContainer}>
      <div className={commonStyles.header}>
        <h1>세션 관리</h1>

        <div className={commonStyles.buttonGroup}>
          <button
            className={commonStyles.iconButton}
            onClick={() => {
              if (!currentSession) {
                toast.error('세션을 먼저 선택해주세요.');
                return;
              }
              // openAddRoundsModal();
            }}
          >
            <div className={commonStyles.iconGroup}>
              <img src={calendarAddIcon} alt="회차 추가" />
              <div className={commonStyles.text}>출석일자 추가</div>
            </div>
          </button>
        </div>
      </div>

      {/*세션 선택 드롭다운 */}
      <div className={styles.selectGroup}>
        <select
          id="sessionSelect"
          className={styles.sessionSelect}
          value={selectedSessionId}
          onChange={(e) => setSelectedSessionId(e.target.value)}
        >
          <option value="" disabled>
            ------ 세션을 선택하세요 ------
          </option>
          {sessionList.map((session) => (
            <option
              key={session.attendanceSessionId}
              value={session.attendanceSessionId}
            >
              {session.title}
            </option>
          ))}
        </select>
      </div>

      {/* 라운드 테이블 (API 연결 전 구조만) */}
      <div className={styles.tableGroup}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>일자</th>
              <th>시간</th>
              <th>가능(분)</th>
              <th>회차</th>
            </tr>
          </thead>
          <tbody>
            {currentDisplayedRounds.length > 0 ? (
              currentDisplayedRounds.map((round, index) => (
                <tr key={round.id}>
                  <td>{formatDate(round.date)}</td>
                  <td>{round.startTime?.substring(0, 5)}</td>
                  <td>{round.availableMinutes}</td>
                  <td>{index + 1}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="4" className={styles.noData}>
                  회차 정보가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default SessionManagementCard;
