import styles from './ExcusedTime.module.css';

const ExcusedTime = () => {
  return (
    <div className={styles.card}>
      <span className={styles.excuse}>1회차 출석 인정 시간</span>
      <span className={styles.time}>18 : 00 PM ~ 18 : 30 PM</span>
    </div>
  );
};

export default ExcusedTime;
