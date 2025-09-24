import styles from './BackTest.module.css';
import StrategyInfoCard from '../components/backtest/StrategyInfoCard';

const BackTest = () => {
  return <div>BackTest Page</div>;
  return (
    <div className={styles.container}>
      <h1 className={styles.pageTitle}>백테스팅</h1>

      <StrategyInfoCard />
    </div>
  );
};

export default BackTest;
