import { useLocation, useNavigate } from 'react-router-dom';
import styles from './BacktestResult.module.css';
import { useState } from 'react';
import BacktestRunResults from '../components/backtest/BacktestRunResults';
import { mapBacktestApiToResultProps } from '../utils/mapBacktestApiToResultProps';
import { api } from '../utils/axios';
import { toast } from 'react-toastify';

const BacktestResult = () => {
  const location = useLocation();
  const navigate = useNavigate();

  // BackTest에서 navigate('/backtest/result', { state: { result: firstResult } }) 로 넘긴 값
  const initialResult = location.state?.result;

  const [rawResult, setRawResult] = useState(initialResult || null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState(null);

  if (!initialResult) {
    return (
      <div style={{ padding: '1.5rem' }}>
        <p>표시할 백테스트 결과 데이터가 없습니다.</p>
        <button type="button" onClick={() => navigate('/backtest')}>
          백테스트 페이지로 돌아가기
        </button>
      </div>
    );
  }

  const mappedProps = mapBacktestApiToResultProps(rawResult);
  if (!mappedProps) {
    return (
      <div style={{ padding: '1.5rem' }}>
        <p>결과 데이터 형식이 올바르지 않습니다.</p>
      </div>
    );
  }

  const runId = rawResult?.backtestRun?.id;
  const status = rawResult?.backtestRun?.status;
  const errorMessage = rawResult?.backtestRun?.errorMessage;

  async function handleRefreshStatus() {
    if (!runId) {
      toast.error('백테스트 ID를 찾을 수 없습니다.');
      return;
    }

    setIsRefreshing(true);
    setRefreshError(null);

    try {
      const response = await api.get(`/api/backtest/runs/${runId}`);
      setRawResult(response.data);
    } catch (error) {
      console.error('Failed to refresh backtest status', error);
      setRefreshError(error);
    } finally {
      setIsRefreshing(false);
    }
  }

  return (
    <div className={styles.resultLayout}>
      <div className={styles.statusBar}>
        <div className={styles.statusInfo}>
          <div>
            <strong>Run ID:</strong> {runId}
          </div>
          <div>
            <strong>현재 상태:</strong> {status}
          </div>
          {errorMessage && (
            <div className={styles.errorMessage}>
              <strong>에러 메시지:</strong> {errorMessage}
            </div>
          )}
          {refreshError && (
            <div className={styles.errorMessage}>
              상태 조회 중 오류: {refreshError.message}
            </div>
          )}
        </div>

        <button
          type="button"
          onClick={handleRefreshStatus}
          disabled={isRefreshing}
          className={styles.refreshButton}
        >
          {isRefreshing ? '상태 조회 중...' : '현재 상태 다시 조회'}
        </button>
      </div>

      <BacktestRunResults {...mappedProps} />
    </div>
  );
};

export default BacktestResult;
