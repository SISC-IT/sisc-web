import styles from './TradingHistory.module.css';
import HistoryCard from './HistoryCard';

const buyMockData = {
  symbol: 'AAPL',
  direction: '매수',
  price: 30,
  count: 3,
};

const sellMockData = {
  symbol: 'AAPL',
  direction: '매도',
  price: 30,
  count: 3,
  profitRate: 0.5,
  profit: 1,
};

const TradingHistory = () => {
  return (
    <div className={styles.container}>
      <select className={styles.selectBox}>
        <option className={styles.option}>2025년 8월 25일 거래내역</option>
      </select>
      <HistoryCard {...buyMockData} />
      <HistoryCard {...sellMockData} />
    </div>
  );
};

export default TradingHistory;
