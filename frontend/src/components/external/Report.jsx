import styles from './Report.module.css';
import { useNavigate } from 'react-router-dom';
import reportCover from '../../assets/external/report/report-cover.png';

const Report = () => {
  const nav = useNavigate();
  return (
    <div className={styles.container}>
      <div
        className={styles.card}
        onClick={() => nav('/main/monthly-report-detail')}
        // API 연동 후 경로 변경 예정
        style={{
          background: `url(${reportCover}) center no-repeat`,
          backgroundSize: 'contain',
        }}
      ></div>
      <span className={styles.title}>
        세투연의 한 달 활동 기록과 주요 성과 요약
      </span>
    </div>
  );
};

export default Report;
