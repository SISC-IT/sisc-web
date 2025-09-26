import styles from './StockGame.module.css';
import DailyBetting from '../components/stockgame/DailyBetting';
import BettingHistory from '../components/stockgame/BettingHistory';

const StockGame = () => {
  return (
    <div className={styles['stock-game']}>
      <div className={styles['title']}>주식베팅</div>
      <div className={styles['daily-betting']}>
        <DailyBetting />
      </div>
      <div className={styles['last-game']}>과거 이력</div>
      <div className={styles['betting-history']}>
        <BettingHistory />
      </div>
    </div>
  );
};

export default StockGame;
