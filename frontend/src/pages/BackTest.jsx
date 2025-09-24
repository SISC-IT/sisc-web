import styles from './BackTest.module.css';
import StrategyInfoCard from '../components/backtest/StrategyInfoCard';
import StocksCard from '../components/backtest/StocksCard';
import EntryRulesCard from '../components/backtest/EntryRulesCard';
import ExitRulesCard from '../components/backtest/ExitRulesCard';

const BackTest = () => {
  return (
    <div className={styles.container}>
      <h1 className={styles.pageTitle}>백테스팅</h1>

      <StrategyInfoCard />
      <StocksCard />
      <EntryRulesCard />
      <ExitRulesCard />
    </div>
  );
};

export default BackTest;
