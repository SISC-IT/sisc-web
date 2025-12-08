import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import styles from './BacktestResult.module.css';
import { useEffect, useState } from 'react';
import BacktestRunResults from '../components/backtest/BacktestRunResults';
import { mapBacktestApiToResultProps } from '../utils/mapBacktestApiToResultProps';
import { api } from '../utils/axios';
import { toast } from 'react-toastify';
import { ImSpinner } from 'react-icons/im';

const FINAL_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED'];

const BacktestResult = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const initialResult = location.state?.result;
  const runIdFromQuery = searchParams.get('runId');

  const [rawResult, setRawResult] = useState(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState(null);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isPollingStatus, setIsPollingStatus] = useState(false);

  useEffect(() => {
    async function init() {
      setIsInitialLoading(true);
      setRefreshError(null);

      try {
        if (runIdFromQuery) {
          const res = await api.get(`/api/backtest/runs/${runIdFromQuery}`);
          setRawResult(res.data);
        } else if (initialResult) {
          setRawResult(initialResult);
        } else {
          setRawResult(null);
        }
      } catch (error) {
        console.error('Failed to load backtest result', error);
        setRefreshError(error);
        toast.error('백테스트 결과를 불러오는 중 오류가 발생했습니다.');
      } finally {
        setIsInitialLoading(false);
      }
    }

    init();
  }, [runIdFromQuery, initialResult]);

  const runId = rawResult?.backtestRun?.id;
  const status = rawResult?.backtestRun?.status;
  const errorMessage = rawResult?.backtestRun?.errorMessage;

  useEffect(() => {
    if (!runId) return;
    if (!status) return;
    const hasMetrics = !!rawResult?.backtestRunMetricsResponse;

    // 이미 최종 상태고 + metrics까지 다 있는 경우에만 폴링 중단
    if (FINAL_STATUSES.includes(status) && hasMetrics) {
      setIsPollingStatus(false);
      return;
    }

    let cancelled = false;
    let timerId = null;

    async function pollOnce() {
      try {
        const res = await api.get(`/api/backtest/runs/${runId}/status`);
        const data = res.data;

        const nextStatus =
          data.backtestRun?.status ??
          data.status ??
          data.backtestRunStatus ??
          null;

        const nextError =
          data.backtestRun?.errorMessage ?? data.errorMessage ?? null;

        if (cancelled) return;

        // status 텍스트 업데이트
        setRawResult((prev) =>
          prev
            ? {
                ...prev,
                backtestRun: {
                  ...prev.backtestRun,
                  status: nextStatus ?? prev.backtestRun.status,
                  errorMessage:
                    nextError ?? prev.backtestRun.errorMessage ?? null,
                },
              }
            : prev
        );

        // COMPLETED면, 실제 결과를 다시 한 번 가져와서 통째로 덮어씀
        if (nextStatus === 'COMPLETED') {
          const fullRes = await api.get(`/api/backtest/runs/${runId}`);
          if (!cancelled) {
            setRawResult(fullRes.data);
            setIsPollingStatus(false);
          }
          return;
        }

        // 실패/취소 같은 최종 상태
        if (nextStatus && FINAL_STATUSES.includes(nextStatus)) {
          setIsPollingStatus(false);
          if (nextStatus === 'FAILED') {
            toast.error('백테스트가 실패했습니다.');
          }
          return;
        }

        // 계속 진행 중이면 다시 폴링
        timerId = setTimeout(pollOnce, 1500);
      } catch (error) {
        console.error('Failed to poll backtest status', error);
        if (!cancelled) {
          setRefreshError(error);
          setIsPollingStatus(false);
          toast.error('백테스트 상태 조회 중 오류가 발생했습니다.');
        }
      }
    }

    setIsPollingStatus(true);
    pollOnce();

    return () => {
      cancelled = true;
      if (timerId) clearTimeout(timerId);
    };
  }, [runId, status, rawResult?.backtestRunMetricsResponse]);

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
      toast.error('백테스트 상태를 다시 조회하는 중 오류가 발생했습니다.');
      setRefreshError(error);
    } finally {
      setIsRefreshing(false);
    }
  }

  async function handleOpenSavedRun(selectedRunId) {
    try {
      setIsRefreshing(true);
      setRefreshError(null);
      const response = await api.get(`/api/backtest/runs/${selectedRunId}`);
      setRawResult(response.data);

      navigate(`/backtest/result?runId=${selectedRunId}`, { replace: true });
    } catch (error) {
      console.error('Failed to open saved backtest run', error);
      toast.error('저장된 백테스트 실행을 여는 중 오류가 발생했습니다.');
      setRefreshError(error);
    } finally {
      setIsRefreshing(false);
    }
  }

  if (!rawResult && !isInitialLoading) {
    return (
      <div style={{ padding: '1.5rem' }}>
        <p className={styles.errorMessage}>
          표시할 백테스트 결과 데이터가 없습니다.
        </p>
        <button
          type="button"
          className={styles.refreshButton}
          onClick={() => navigate('/backtest')}
        >
          백테스트 페이지로 돌아가기
        </button>
      </div>
    );
  }

  if (isInitialLoading || !rawResult) {
    return (
      <div className={styles.loadingState}>
        <ImSpinner className={styles.spinner} />
        <p>백테스트 결과를 불러오는 중입니다...</p>
      </div>
    );
  }

  const mappedProps = mapBacktestApiToResultProps(rawResult);
  if (!mappedProps) {
    return (
      <div style={{ padding: '1.5rem' }}>
        <p className={styles.errorMessage}>
          결과 데이터 형식이 올바르지 않습니다.
        </p>
      </div>
    );
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
            {isPollingStatus && (
              <span
                style={{ marginLeft: 8, fontSize: '0.8rem', color: '#6b7280' }}
              >
                (자동으로 상태를 확인 중...)
              </span>
            )}
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

      <BacktestRunResults
        {...mappedProps}
        runId={runId}
        onOpenSavedRun={handleOpenSavedRun}
      />
    </div>
  );
};

export default BacktestResult;
