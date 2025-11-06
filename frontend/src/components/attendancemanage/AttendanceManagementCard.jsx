import styles from './AttendanceManagementCard.module.css';

const AttendanceManagementCard = ({
  styles: commonStyles,
  sessions,
  selectedSessionId,
  selectedRound,
  onAttendanceChange,
}) => {
  const selectedSession = sessions.find(
    (session) => session.id === selectedSessionId
  );

  const selectedRoundData = selectedSession?.round.find(
    (round) => round.id === selectedRound
  );

  const participants = selectedRoundData?.participants || [];

  return (
    <div className={styles.attendanceManagementCardContainer}>
      <header className={commonStyles.header}>
        <h1 className={commonStyles.title}>출석 관리</h1>
      </header>
      <div className={styles.tableGroup}>
        <table className={styles.table} role="grid">
          <thead>
            <tr>
              <th>이름</th>
              <th>상태</th>
              <th>변경</th>
              <th>횟수</th>
            </tr>
          </thead>
          <tbody>
            {participants.length > 0
              ? participants.map((participant) => (
                  <tr key={participant.memberId}>
                    <td>{participant.name}</td>
                    <td>{participant.attendance}</td>
                    <td>
                      <select
                        className={styles.attendanceSelect}
                        value={participant.attendance}
                        onChange={(e) =>
                          onAttendanceChange(
                            participant.memberId,
                            e.target.value
                          )
                        }
                      >
                        <option value="출석">출석</option>
                        <option value="결석">결석</option>
                      </select>
                    </td>
                    <td>-</td>
                  </tr>
                ))
              : []}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AttendanceManagementCard;
