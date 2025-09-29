import styles from './BettingHistory.module.css';
import icon1 from '../../assets/at_icon_1.png';
import StockInfoItem from './StockInfoItem';

const mockBetHistory = [
  {
    date: '2025-08-24',
    symbol: 'AAPL',
    closePrice: 96.3,
    nextClosePrice: 76.4,
    changePercent: -0.6,
    points: 300,
    participants: 200,
    result: 'DOWN',
  },
];

const BettingHistory = () => {
  const formatDate = (dateStr) => {
    const [year, month, day] = dateStr.split('-');
    return `${year}년 ${month}월 ${day}일`;
  };

  return (
    <div className={styles['daily-betting-card']}>
      {/* map 함수 사용해서 반복 렌더링 해야함(적용 예정) */}
      <span className={styles['date']}>
        {formatDate(mockBetHistory[0].date)} 베팅
      </span>
      <div className={styles['stock-info']}>
        <StockInfoItem label="종목" value={mockBetHistory[0].symbol} />
        <StockInfoItem
          label="다음 날 종가"
          value={mockBetHistory[0].nextClosePrice}
        />
        <StockInfoItem label="종가" value={mockBetHistory[0].closePrice} />
        <div className={styles['stock-change']}>
          <span className={styles['change-value']}>{'->'}</span>
          <span className={styles['change-value']}>
            {mockBetHistory[0].changePercent}%
          </span>
        </div>
        {/* 베팅 결과 */}
        <div className={styles['bet-result']}>
          <div className={styles['bet-point']}>
            <img src={icon1} className={styles['icon']} />+
            <span>{mockBetHistory[0].points}P</span>
          </div>
          <div className={styles['divider']} />
          <span>{mockBetHistory[0].participants}명</span>
          {mockBetHistory.result === 'UP' ? (
            <button className={styles['up-button']}>상승 ↑</button>
          ) : (
            <button className={styles['down-button']}>하락 ↓</button>
          )}
        </div>
      </div>
    </div>
  );
};

export default BettingHistory;
