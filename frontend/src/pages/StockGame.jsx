import styles from './StockGame.module.css';
import Betting from '../components/stockgame/Betting';
import BettingHistory from '../components/stockgame/BettingHistory';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const StockGame = () => {
  const [active, setactive] = useState('progress');
  const [lastGame, setLastGame] = useState('weekly');
  const nav = useNavigate();

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      alert('로그인 후 이용하실 수 있습니다.');
      nav('/login');
    }
  }, []);

  return (
    <div className={styles['stock-game']}>
      <h1 className={styles['title']}>주식베팅</h1>
      <div className={styles['content-tab']}>
        <button
          onClick={() => setactive('progress')}
          className={`${styles.tab} ${active === 'progress' ? styles.active : ''}`}
        >
          진행률
        </button>
        <button
          onClick={() => setactive('history')}
          className={`${styles.tab} ${active === 'history' ? styles.active : ''}`}
        >
          이력
        </button>
      </div>
      {active === 'progress' ? (
        <div className={styles['daily-betting']}>
          <span>일간</span>
          <Betting period={'daily'} />
          <span>주간</span>
          <Betting period={'weekly'} />
        </div>
      ) : (
        <div className={styles['betting-history']}>
          <button
            onClick={() => setLastGame('weekly')}
            className={`${styles.type} ${lastGame === 'weekly' ? styles.selected : ''}`}
          >
            주간
          </button>
          <button
            onClick={() => setLastGame('daily')}
            className={`${styles.type} ${lastGame === 'daily' ? styles.selected : ''}`}
          >
            일간
          </button>
          <BettingHistory type={lastGame} />
        </div>
      )}
    </div>
  );
};

export default StockGame;
