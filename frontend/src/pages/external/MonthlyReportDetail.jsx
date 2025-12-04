import styles from './MonthlyReportDetail.module.css';
import logo from '../../assets/logo.png';
import { useNavigate } from 'react-router-dom';

const MonthlyReportDetail = () => {
  const nav = useNavigate();
  return (
    <div className={styles.container}>
      <header
        className={styles.header}
        onClick={() => nav('/main/monthly-report')}
      >
        <img src={logo} alt="로고" className={styles.logo} />
        <span className={styles.title}>월간 세투연</span>
      </header>
      <div className={styles.content}></div>
    </div>
  );
};

export default MonthlyReportDetail;
