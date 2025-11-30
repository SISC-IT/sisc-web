import styles from './HistoryCard.module.css';

const HistoryCard = ({
  symbol,
  direction,
  price,
  count,
  profitRate,
  profit,
}) => {
  const isBuy = direction === '매수';

  return (
    <div className={styles.tradeCard}>
      <div className={styles.info}>
        <span className={styles.label}>종목</span>
        <span className={styles.value}>{symbol}</span>
      </div>

      <div className={styles.info}>
        <span className={styles.label}>포지션</span>
        <span className={isBuy ? styles.buy : styles.sell}>{direction}</span>
      </div>

      <div className={styles.info}>
        <span className={styles.label}>가격</span>
        <span className={styles.value}>{price}$</span>
      </div>

      <div className={styles.info}>
        <span className={styles.label}>수량</span>
        <span className={styles.value}>{count}주</span>
      </div>

      {/* 매도일 때만 표시 */}
      {!isBuy && (
        <>
          <div className={styles.info}>
            <span className={styles.label}>수익률</span>
            <span className={styles.value}>{profitRate}%</span>
          </div>
          <div className={styles.info}>
            <span className={styles.label}>수익</span>
            <span className={styles.value}>{profit}$</span>
          </div>
        </>
      )}
    </div>
  );
};

export default HistoryCard;
