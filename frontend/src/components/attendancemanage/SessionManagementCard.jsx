import styles from './SessionManagementCard.module.css';

const SessionManagementCard = ({
  styles: commonStyles,
  sessions,
  selectedSessionId,
  setSelectedSessionId,
  selectedRound,
  setSelectedRound,
}) => {
  const currendSession = sessions.find(
    (session) => session.id === selectedSessionId
  );
  const currentDisplayedRounds = currendSession ? currendSession.round : [];

  return (
    <div className={styles.sessionManagementCardContainer}>
      <div className={commonStyles.header}>
        <h1 className={commonStyles.title}>세션 관리</h1>
      </div>
      <div className={styles.selectGroup}>
        <select
          id="sessionSelect"
          className={styles.sessionSelect}
          value={selectedSessionId || ''}
          onChange={(e) => setSelectedSessionId(e.target.value)}
        >
          {!selectedSessionId ? (
            <option value="" disabled>
              세션을 선택하세요
            </option>
          ) : (
            ' '
          )}
          {sessions.map((session) => (
            <option key={session.id} value={session.id}>
              {session.title}
            </option>
          ))}
        </select>
      </div>
      <div className={styles.tableGroup}>
        <table className={styles.table} role="grid">
          <thead>
            <tr>
              <th>일자</th>
              <th>시간</th>
              <th>가능(분)</th>
              <th>메뉴</th>
            </tr>
          </thead>
          <tbody>
            {currentDisplayedRounds.map((round) => {
              return (
                <tr
                  key={round.id}
                  className={`${styles.row} ${selectedRound === round.id ? styles.selectedRound : ''}`}
                  onClick={() => setSelectedRound(round.id)}
                >
                  <td>{round.date}</td>
                  <td>{round.startTime}</td>
                  <td>{round.availableMinutes}</td>
                  <td>
                    <button
                      className={styles.menuButton}
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedRound(null);
                      }}
                    >
                      ···
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default SessionManagementCard;
