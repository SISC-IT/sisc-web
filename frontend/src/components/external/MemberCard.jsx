import styles from './MemberCard.module.css';

const MemberCard = ({ datas }) => {
  return (
    <div className={styles.container}>
      {datas.map((data) => {
        return (
          <div className={styles.cardSection}>
            <div className={styles.card}></div>
            <div className={styles.nameSection}>
              <span className={styles.role}>{data.role}</span>
              <span className={styles.name}>{data.name}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default MemberCard;
