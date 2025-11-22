import styles from './Betting.module.css';
import icon1 from '../../assets/at_icon_1.png';
import StockInfoItem from './StockInfoItem';
import { useState, useEffect } from 'react';
import { dailyBet, weeklyBet } from '../../utils/bettingInfo';
import {
  getDailyBetHistory,
  getWeeklyBetHistory,
} from '../../utils/bettingHistory';
import { api } from '../../utils/axios';

const Betting = ({ period }) => {
  const [isBetting, setIsBetting] = useState('none');
  const [data, setData] = useState(null);
  const [userBets, setUserBets] = useState(null);

  useEffect(() => {
    async function fetchData() {
      if (period === 'daily') {
        const res = await dailyBet();
        setData(res);
        const history = await getDailyBetHistory();
        setUserBets(history[0]);
      } else {
        const res = await weeklyBet();
        setData(res);
        const history = await getWeeklyBetHistory();
        setUserBets(history[0]);
      }
    }
    fetchData();
  }, [period]);

  // userBets 또는 data가 바뀔 때 베팅 여부 체크
  useEffect(() => {
    if (!data || !userBets) {
      setIsBetting('none');
      return;
    }

    if (data.betRoundId === userBets.betRoundId) {
      userBets.option === 'RISE' ? setIsBetting('up') : setIsBetting('down');
    } else {
      setIsBetting('none');
    }
  }, [data, userBets]);

  const onClickUpBet = async () => {
    if (isBetting !== 'none') {
      alert('이미 베팅하셨습니다.');
    } else if (confirm('상승에 베팅하시겠습니까?')) {
      try {
        const res = await api.post('/api/user-bets', {
          roundId: data.betRoundId,
          option: 'RISE',
          stakePoints: 0,
          isFree: true,
        });
        setIsBetting('up');
        return res;
      } catch (error) {
        console.log(error.message);
        return null;
      }
    }
  };

  const onClickDownBet = async () => {
    if (isBetting !== 'none') {
      alert('이미 베팅하셨습니다.');
    } else if (confirm('하락에 베팅하시겠습니까?')) {
      try {
        const res = await api.post('/api/user-bets', {
          roundId: data.betRoundId,
          option: 'FALL',
          stakePoints: 0,
          isFree: true,
        });
        setIsBetting('down');
        return res;
      } catch (error) {
        console.log(error.message);
        return null;
      }
    }
  };

  const onClickCancelBet = async () => {
    if (confirm('베팅을 취소하시겠습니까?')) {
      try {
        await api.delete(`/api/user-bets/${userBets.userBetId}`);
        setIsBetting('none');
      } catch (error) {
        console.log(error);
      }
    }
  };

  if (!data) return <div>Loading...</div>;

  return (
    <div className={styles['daily-betting-card']}>
      <span className={styles['date']}>{data.title}</span>
      {/* 베팅종목 정보 */}
      <div className={styles['stock-info']}>
        <StockInfoItem label="종목" value={data.symbol} />
        <StockInfoItem label="종가" value={data.previousClosePrice} />
        {/* 베팅 정보 */}
        <div className={styles['bet-info']}>
          {/* 상승 베팅 */}
          <div className={styles['upper-bet']}>
            <div className={styles['bet-point']}>
              <img src={icon1} className={styles['icon']} />
              <span>+{data.upTotalPoints}P</span>
            </div>
            <div className={styles['divider']} />
            <span>{data.upBetCount}명</span>
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
            <div className={styles['bet-point']}>
              <img src={icon1} className={styles['icon']} />
              <span>-{data.downTotalPoints}P</span>
            </div>
            <div className={styles['divider']} />
            <span>{data.downBetCount}명</span>
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

export default Betting;
