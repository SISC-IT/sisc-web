import styles from './TradingCard.module.css';

const mockData = {
  profitRate: 28.4,
  tradingPeriod: '2025.08.01 ~ 2025.08.31',
  initialAsset: 100_000_000,
  totalAsset: 128_540_000,
};

const TradingCard = () => {
  return (
    <div className={styles.container}>
      <div className={styles.profitCard}>
        <span className={styles.title}>누적 수익률</span>
        <div className={styles.info}>
          <span className={styles.profitValue}>{mockData.profitRate}%</span>
          <span className={styles.label}>초기대비</span>
        </div>
      </div>
      <div className={styles.tradingPeriodCard}>
        <span className={styles.title}>매매 기간</span>
        <div className={styles.info}>
          <span className={styles.periodValue}>{mockData.tradingPeriod}</span>
          <span className={styles.label}>
            초기자산 ₩ {mockData.initialAsset.toLocaleString()}
          </span>
        </div>
      </div>
      <div className={styles.totalCard}>
        <span className={styles.title}>총 자산</span>
        <div className={styles.info}>
          <span className={styles.totalValue}>
            ₩ {mockData.totalAsset.toLocaleString()}
          </span>
          <span className={styles.label}>
            초기자산 ₩ {mockData.initialAsset.toLocaleString()}
          </span>
        </div>
      </div>
    </div>
  );
};

export default TradingCard;
