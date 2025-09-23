import RosterRow from './RosterRow';
const RosterTable = ({ styles, roster, statuses, onChangeStatus }) => {
  return (
    <div className={styles.table}>
      <div className={styles.names}>
        <div>이름</div>
        <div>상태</div>
        <div>변경</div>
      </div>

      <div className={styles.rosterScroll}>
        {roster.map((m) => (
          <RosterRow
            key={m.id}
            styles={styles}
            member={m}
            statuses={statuses}
            onChangeStatus={onChangeStatus}
          />
        ))}
        {roster.length === 0 && (
          <div className={styles.trow}>
            <div className={styles.tcell}>
              선택된 세션에 등록된 명단이 없습니다.
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default RosterTable;
