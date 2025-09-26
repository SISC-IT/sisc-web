import styles from './DailyBetting.module.css';
import icon1 from '../../assets/at_icon_1.png';
import StockInfoItem from './StockInfoItem';

const mockDailyBet = {
  date: '2025-08-25',
  symbol: 'AAPL',
  closePrice: 96.3,
  upperBet: 300,
  lowerBet: 300,
  upBetCount: 200,
  downBetCount: 200,
};

const DailyBetting = () => {
  const formatDate = (dateStr) => {
    const [year, month, day] = dateStr.split('-');
    return `${year}년 ${month}월 ${day}일`;
  };

  return (
    <div className={styles['daily-betting-card']}>
      <span className={styles['date']}>
        {formatDate(mockDailyBet.date)} 베팅
      </span>
      {/* 베팅종목 정보 */}
      <div className={styles['stock-info']}>
        <StockInfoItem label="종목" value={mockDailyBet.symbol} />
        <StockInfoItem label="종가" value={mockDailyBet.closePrice} />
        {/* 베팅 정보 */}
        <div className={styles['bet-info']}>
          {/* 상승 베팅 */}
          <div className={styles['upper-bet']}>
            <div className={styles['bet-point']}>
              <img src={icon1} className={styles['icon']} />
              <span>+{mockDailyBet.upperBet}P</span>
            </div>
            <div className={styles['divider']} />
            <span>{mockDailyBet.upBetCount}명</span>
            <button className={styles['up-button']}>상승 ↑</button>
          </div>
          {/* 하락 베팅 */}
          <div className={styles['lower-bet']}>
            <div className={styles['bet-point']}>
              <img src={icon1} className={styles['icon']} />
              <span>-{mockDailyBet.lowerBet}P</span>
            </div>
            <div className={styles['divider']} />
            <span>{mockDailyBet.downBetCount}명</span>
            <button className={styles['down-button']}>하락 ↓</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DailyBetting;
