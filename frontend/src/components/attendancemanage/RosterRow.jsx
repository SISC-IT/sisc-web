const RosterRow = ({ styles, member, statuses, onChangeStatus }) => {
  return (
    <div className={styles.trow}>
      <div className={styles.tcell}>{member.name}</div>
      <div className={`${styles.tcell} ${styles.muted}`}>{member.status}</div>
      <div className={styles.tcell}>
        <select
          className={`${styles.select} ${styles.compact}`}
          value={member.status}
          onChange={(e) => onChangeStatus(member.id, e.target.value)}
        >
          {statuses.map((st) => (
            <option key={st} value={st}>
              {st}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
};
export default RosterRow;
