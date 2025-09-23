const SessionSelect = ({
  styles,
  sessions,
  selectedId,
  onChange,
  sessionLabel,
}) => {
  return (
    <div className={styles.field}>
      <label htmlFor="session-select" className={styles.srOnly}>세션선택</label>
      <select
        id="session-select"
        className={styles.select}
        value={selectedId}
        onChange={(e) => onChange(e.target.value)}
      >
        {sessions
          .slice()
          .sort((a, b) => b.createdAt - a.createdAt)
          .map((s) => (
            <option key={s.id} value={s.id}>
              {sessionLabel(s)}
            </option>
          ))}
      </select>
    </div>
  );
};

export default SessionSelect;
