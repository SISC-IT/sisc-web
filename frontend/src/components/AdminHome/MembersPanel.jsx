import MemberList from './MemberList';

const MembersPanel = ({ members = [], styles }) => {
  return (
    <section className={styles.panel}>
      <div className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>회원 목록</h2>
      </div>
      <MemberList members={members} styles={styles} />
    </section>
  );
};

export default MembersPanel;
