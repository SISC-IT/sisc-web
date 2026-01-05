import { useAttendance } from '../../contexts/AttendanceContext';
import styles from './SessionManagementCard.module.css';
import calendarAddIcon from '../../assets/calendar-icon.svg';

import { getRounds } from '../../utils/attendanceManage';
import { useEffect, useState } from 'react';
import { toast } from 'react-toastify';

// 날짜 포맷 함수 추가
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${month}/${day}`;
};

// 시간 포맷 함수 추가 (초 제거)
const formatTime = (timeStr) => {
  if (!timeStr) return '';
  return timeStr.substring(0, 5); // "14:30:00" -> "14:30"
};

const SessionManagementCard = ({ styles: commonStyles }) => {
  const {
    sessions,
    selectedSessionId,
    setSelectedSessionId,
    selectedRound,
    setSelectedRound,
    setEditingRound,
    setEditingSession,
    openRoundModifyModal,
    openSessionModifyModal,
    openAddRoundsModal,
    roundsVersion,
    openAddUsersModal,
  } = useAttendance();

  const [currentDisplayedRounds, setCurrentDisplayedRounds] = useState([]);

  const currentSession = sessions.find(
    (session) => session.attendanceSessionId === selectedSessionId
  );

  useEffect(() => {
    const fetchRounds = async () => {
      if (selectedSessionId) {
        setCurrentDisplayedRounds([]);
        const rounds = await getRounds(selectedSessionId);

        const sortedRounds = (rounds || []).sort(
          (a, b) =>
            new Date(`${a.date}T${a.startTime}`) -
            new Date(`${b.date}T${b.startTime}`)
        );
        setCurrentDisplayedRounds(sortedRounds);
      } else {
        setCurrentDisplayedRounds([]);
      }
    };
    fetchRounds();
  }, [selectedSessionId, roundsVersion]);

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
              openAddRoundsModal();
            }}
          >
            <div className={commonStyles.iconGroup}>
              <img src={calendarAddIcon} alt="회차 추가" />
              <div className={commonStyles.text}>출석일자 추가</div>
            </div>
          </button>
          <button
            className={commonStyles.iconButton}
            onClick={() => {
              if (!currentSession) {
                toast.error('세션을 먼저 선택해주세요.');
                return;
              }
              openAddUsersModal();
            }}
          >
            <div className={commonStyles.iconGroup}>
              <img src={calendarAddIcon} alt="사용자 추가" />
              <div className={commonStyles.text}>세션에 사용자 추가</div>
            </div>
          </button>
        </div>
      </div>
      <div className={styles.selectGroup}>
        <select
          id="sessionSelect"
          className={styles.sessionSelect}
          value={selectedSessionId || ''}
          onChange={(e) => {
            setSelectedSessionId(e.target.value);
            setSelectedRound(null);
          }}
        >
          <option value="" disabled>
            ------ 세션을 선택하세요 ------
          </option>
          {sessions.map((session) => (
            <option
              key={session.attendanceSessionId}
              value={session.attendanceSessionId}
            >
              {session.title}
            </option>
          ))}
        </select>
        <button
          type="button"
          className={styles.menuButton}
          onClick={() => {
            if (currentSession) {
              setEditingSession(currentSession);
              openSessionModifyModal();
            } else {
              alert('세션을 선택해주세요.');
            }
          }}
        >
          ···
        </button>
      </div>
      <div className={styles.tableGroup}>
        <table className={styles.table} role="grid">
          <thead>
            <tr>
              <th>일자</th>
              <th>시간</th>
              <th>가능(분)</th>
              <th>회차</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {currentDisplayedRounds.length > 0 ? (
              currentDisplayedRounds.map((round, index) => {
                return (
                  <tr
                    key={round.id}
                    className={`${styles.row} ${selectedRound === round.id ? styles.selectedRound : ''}`}
                    onClick={() => setSelectedRound(round.id)}
                    title={`${round.date} ${round.startTime}`}
                  >
                    <td className={styles.dateCell}>{formatDate(round.date)}</td>
                    <td className={styles.timeCell}>{formatTime(round.startTime)}</td>
                    <td className={styles.minutesCell}>{round.availableMinutes}</td>
                    <td className={styles.roundCell}>{index + 1}</td>
                    <td className={styles.menuCell}>
                      <button
                        className={styles.menuButton}
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedRound(null);
                          setEditingRound(round);
                          openRoundModifyModal();
                        }}
                      >
                        ···
                      </button>
                    </td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td colSpan="5" className={styles.noData}>
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
