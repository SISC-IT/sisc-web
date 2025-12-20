import styles from './External.module.css';
import Filter from '../../components/external/Filter';

const teams = [
  '증권 1팀',
  '증권 2팀',
  '증권 3팀',
  '자산 운용팀',
  '금융 IT팀',
  '매크로팀',
  '트레이딩팀',
];

const Leaders = () => {
  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.title}>임원진 소개</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}>
        <Filter items={teams} />
      </div>
    </div>
  );
};

export default Leaders;
