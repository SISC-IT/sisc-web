import styles from './External.module.css';
import Filter from '../../components/external/Filter';

const cohort = Array.from({ length: 24 }, (_, i) => `${24 - i}기`);

const Intro = () => {
  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.title}>동아리 소개</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}>
        <Filter items={cohort} />
      </div>
    </div>
  );
};

export default Intro;
