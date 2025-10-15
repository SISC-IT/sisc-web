import styles from './StockGame.module.css';
import DailyBetting from '../components/stockgame/DailyBetting';
import BettingHistory from '../components/stockgame/BettingHistory';
import { useState } from 'react';

const StockGame = () => {
  const [active, setactive] = useState('progress');
  const [lastGame, setLastGame] = useState('weekly');

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
          <DailyBetting />
          <span>주간</span>
          <DailyBetting />
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
