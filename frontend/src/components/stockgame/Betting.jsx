import styles from './Betting.module.css';
import icon1 from '../../assets/at_icon_1.png';
import StockInfoItem from './StockInfoItem';
// import { mockDailyBet, mockWeeklyBet } from '../../utils/bettingInfo';
import { useState } from 'react';
import { dailyBet, weeklyBet } from '../../utils/bettingInfo';

const DailyBetting = ({ period }) => {
  const [isBetting, setIsBetting] = useState('none');
  // const bettingInfo = period === 'daily' ? mockDailyBet : mockWeeklyBet;
  const bettingInfo = period === 'daily' ? dailyBet() : weeklyBet();

  const onClickUpBet = () => {
    if (isBetting !== 'none') {
      alert('이미 베팅하셨습니다.');
    } else if (confirm('상승에 베팅하시겠습니까?')) {
      setIsBetting('up');
    }
  };

  const onClickDownBet = () => {
    if (isBetting !== 'none') {
      alert('이미 베팅하셨습니다.');
    } else if (confirm('하락에 베팅하시겠습니까?')) {
      setIsBetting('down');
    }
  };

  const onClickCancelBet = () => {
    if (confirm('베팅을 취소하시겠습니까?')) {
      setIsBetting('none');
    }
  };

  const formatDate = (dateStr) => {
    const date = new Date(dateStr);
    const kst = new Date(date.getTime() + 9 * 60 * 60 * 1000); // +9시간
    const year = kst.getFullYear();
    const month = String(kst.getMonth() + 1).padStart(2, '0');
    const day = String(kst.getDate()).padStart(2, '0');
    return `${year}년 ${month}월 ${day}일`;
  };

  return (
    <div className={styles['daily-betting-card']}>
      <span className={styles['date']}>
        {formatDate(bettingInfo.openAt)} 베팅
      </span>
      {/* 베팅종목 정보 */}
      <div className={styles['stock-info']}>
        <StockInfoItem label="종목" value={bettingInfo.symbol} />
        <StockInfoItem label="종가" value={bettingInfo.previousClosePrice} />
        {/* 베팅 정보 */}
        <div className={styles['bet-info']}>
          {/* 상승 베팅 */}
          <div className={styles['upper-bet']}>
            {/* <div className={styles['bet-point']}>
              <img src={icon1} className={styles['icon']} />
              <span>+{bettingInfo.upperBet}P</span>
            </div>
            <div className={styles['divider']} />
            <span>{bettingInfo.upBetCount}명</span> */}
            <button
              className={`${styles['up-button']} ${isBetting === 'up' && styles.upActive}`}
              onClick={onClickUpBet}
            >
              상승 ↑
              {isBetting === 'up' && <div className={styles.upBetCheck}>v</div>}
            </button>
          </div>
          {/* 하락 베팅 */}
          <div className={styles['lower-bet']}>
            {/* <div className={styles['bet-point']}>
              <img src={icon1} className={styles['icon']} />
              <span>-{bettingInfo.lowerBet}P</span>
            </div>
            <div className={styles['divider']} />
            <span>{bettingInfo.downBetCount}명</span> */}
            <button
              className={`${styles['down-button']} ${isBetting === 'down' && styles.downActive}`}
              onClick={onClickDownBet}
            >
              하락 ↓
              {isBetting === 'down' && (
                <div className={styles.downBetCheck}>v</div>
              )}
            </button>
          </div>
        </div>
        {isBetting !== 'none' && (
          <span className={styles.cancel} onClick={onClickCancelBet}>
            X 취소하기
          </span>
        )}
      </div>
    </div>
  );
};

export default DailyBetting;
