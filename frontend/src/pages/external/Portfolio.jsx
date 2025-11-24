import styles from './External.module.css';

const Portfolio = () => {
  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.title}>운용 포트폴리오</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}></div>
    </div>
  );
};

export default Portfolio;
