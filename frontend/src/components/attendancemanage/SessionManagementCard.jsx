import styles from './SessionManagementCard.module.css';
import calendarAddIcon from '../../assets/calendar-icon.svg';

import { useContext, useEffect, useState } from 'react';
import { toast } from 'react-toastify';
import { useAttendance } from '../../contexts/AttendanceContext';
import { getRounds, addRound } from '../../utils/attendanceManage';
import RoundDayPicker from './RoundDayPicker';

// 날짜 포맷 함수
const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const date = new Date(dateStr);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${month}/${day}`;
};

const SessionManagementCard = ({ styles: commonStyles }) => {
  const {
    sessions,
    roundsVersion,
    handleAddRounds,
    openAddRoundsModal,
    selectedSessionId,
    setSelectedSessionId,
  } = useAttendance();
  const [currentDisplayedRounds, setCurrentDisplayedRounds] = useState([]);

  const sessionList = sessions || [];

  const currentSession = sessionList.find(
    (session) => String(session.sessionId) === String(selectedSessionId)
  );

  useEffect(() => {
    const fetchRounds = async () => {
      if (!selectedSessionId) {
        setCurrentDisplayedRounds([]);
        return;
      }

      try {
        const rounds = await getRounds(selectedSessionId);
        setCurrentDisplayedRounds(rounds || []);
      } catch (e) {
        toast.error('라운드를 불러오지 못했습니다.');
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
        </div>
      </div>

      {/*세션 선택 드롭다운 */}
      <div className={styles.selectGroup}>
        <select
          value={selectedSessionId || ''}
          onChange={(e) => setSelectedSessionId(e.target.value)}
        >
          <option value="" disabled>
            ------ 세션을 선택하세요 ------
          </option>

          {sessionList.map((session) => (
            <option key={session.sessionId} value={session.sessionId}>
              {session.session.title}
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
              <th>QR 코드</th>
            </tr>
          </thead>
          <tbody>
            {currentDisplayedRounds.length > 0 ? (
              currentDisplayedRounds.map((round, index) => {
                const startTime = new Date(round.startAt);
                const closeTime = new Date(round.closeAt);

                const minutes = Math.floor((closeTime - startTime) / 60000);

                return (
                  <tr key={round.roundId}>
                    <td>{formatDate(round.roundDate)}</td>
                    <td>
                      {startTime.toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </td>
                    <td>{minutes}</td>
                    <td>{index + 1}</td>
                    <td>
                      <button
                        className={styles.qrButton}
                        onClick={() =>
                          window.open(
                            `/attendance/admin/qr?roundId=${round.roundId}`,
                            '_blank'
                          )
                        }
                      >
                        QR 생성
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
