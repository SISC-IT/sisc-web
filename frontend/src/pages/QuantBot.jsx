import styles from './QuantBot.module.css';
import TradingCard from '../components/quantbot/TradingCard';
import TradingHistory from '../components/quantbot/TradingHistory';

const QuantBot = () => {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>퀀트 트레이딩</h1>
      <TradingCard />
      <div className={styles.historySection}>
        <h2 className={styles.subTitle}>과거 이력</h2>
        <TradingHistory />
      </div>
    </div>
  );
};

export default QuantBot;
