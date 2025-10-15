import styles from './BettingHistory.module.css';
import icon1 from '../../assets/at_icon_1.png';
import StockInfoItem from './StockInfoItem';
import { dailyBettingHistory } from '../../utils/dailyBettingHistory';
import { weekelyBettingHistory } from '../../utils/weeklyBettingHIstory';

const BettingHistory = ({ type }) => {
  const formatDate = (dateStr) => {
    const [year, month, day] = dateStr.split('-');
    return `${year}년 ${month}월 ${day}일`;
  };

  const mockBetHistory =
    type === 'weekly' ? weekelyBettingHistory : dailyBettingHistory;

  return (
    <>
      {mockBetHistory.map((history, index) => (
        <div
          key={index}
          className={`${styles['daily-betting-card']} ${history.isCorrect ? styles['correct-card'] : styles['incorrect-card']}`}
        >
          <button
            className={`${styles['result-icon']} ${history.isCorrect ? styles.correct : styles.incorrect}`}
          >
            {history.isCorrect ? 'x' : 'v'}
          </button>
          <span className={styles['date']}>
            {formatDate(history.date)} 베팅
          </span>
          <div className={styles['stock-info']}>
            <StockInfoItem label="종목" value={history.symbol} />
            <StockInfoItem
              label="다음 날 종가"
              value={history.nextClosePrice}
            />
            <StockInfoItem label="종가" value={history.closePrice} />
            <div className={styles['stock-change']}>
              <span className={styles['change-value']}>{'->'}</span>
              <span className={styles['change-value']}>
                {history.changePercent}%
              </span>
            </div>
            {/* 베팅 결과 */}
            <div className={styles['bet-result']}>
              <div className={styles['bet-point']}>
                <img src={icon1} className={styles['icon']} />+
                <span>{history.points}P</span>
              </div>
              <div className={styles['divider']} />
              <span>{history.participants}명</span>
              {history.result === 'UP' ? (
                <button className={styles['up-button']}>상승 ↑</button>
              ) : (
                <button className={styles['down-button']}>하락 ↓</button>
              )}
            </div>
          </div>
        </div>
      ))}
    </>
  );
};

export default BettingHistory;
