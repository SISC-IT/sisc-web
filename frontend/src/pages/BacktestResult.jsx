import { useParams } from 'react-router-dom';
import BacktestResultsWithTemplates from '../components/backtest/BacktestResultsWithTemplates';
import useBacktestRunResult from '../hooks/useBacktestRunResult';
import styles from './BacktestResult.module.css';

export default function BacktestResult() {
  // 예: /backtests/runs/:runId 같은 라우트라고 가정
  const { runId } = useParams();
  const { isLoading, error, resultProps } = useBacktestRunResult(runId);

  if (!runId) {
    return <div className={styles.center}>runId가 없습니다.</div>;
  }

  if (isLoading) {
    return <div className={styles.center}>백테스트 결과를 불러오는 중...</div>;
  }

  if (error) {
    return (
      <div className={styles.center}>
        <p>결과를 불러오는 중 오류가 발생했습니다.</p>
        <p className={styles.errorMessage}>{error.message}</p>
      </div>
    );
  }

  if (!resultProps) {
    return <div className={styles.center}>표시할 결과가 없습니다.</div>;
  }

  return (
    <div className={styles.wrapper}>
      <BacktestResultsWithTemplates {...resultProps} />
    </div>
  );
}
