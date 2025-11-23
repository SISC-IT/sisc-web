import styles from './External.module.css';

const Intro = () => {
  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.title}>동아리 소개</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}></div>
    </div>
  );
};

export default Intro;
