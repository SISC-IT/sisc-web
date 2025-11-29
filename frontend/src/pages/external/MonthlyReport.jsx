import styles from './MonthlyReport.module.css';
import logo from '../../assets/logo.png';
import Report from '../../components/external/Report';

const MonthlyReport = () => {
  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div className={styles.logoSection}>
          <img src={logo} alt="세종투자연구회" className={styles.logo} />
          <span className={styles.logoName}>월간 세투연</span>
        </div>
        <h1 className={styles.title}>
          매월 업데이트되는{' '}
          <strong className={styles.strong}>세투연 콘텐츠</strong>를 <br /> 한
          곳에 모았습니다.
        </h1>
        <h2 className={styles.subTitle}>
          지난 한 달의 활동과 자료들을 아카이브 형식으로 정리했어요.
        </h2>
      </div>
      <div className={styles.reportSection}>
        <Report />
        <Report />
        <Report />
        <Report />
      </div>
    </div>
  );
};

export default MonthlyReport;
