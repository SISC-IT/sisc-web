import styles from './BackTest.module.css';
import StrategyInfoCard from '../components/backtest/StrategyInfoCard';
import StocksCard from '../components/backtest/StocksCard';

const BackTest = () => {
  return (
    <div className={styles.container}>
      <h1 className={styles.pageTitle}>백테스팅</h1>

      <StrategyInfoCard />
      <StocksCard />
    </div>
  );
};

export default BackTest;
