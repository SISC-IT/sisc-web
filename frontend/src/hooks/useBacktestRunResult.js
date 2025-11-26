import { useEffect, useState } from 'react';
import { mapBacktestApiToResultProps } from '../utils/mapBacktestApiToResultProps';

// runId를 받아서
// 1) 서버로부터 결과 조회
// 2) util로 props 형태로 변환
// 3) 컴포넌트에서 바로 쓸 수 있게 상태로 제공
export default function useBacktestRunResult(runId) {
  const [resultProps, setResultProps] = useState(null);
  const [rawResponse, setRawResponse] = useState(null); // 디버깅이나 추가 UI용
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!runId) return;

    let isCancelled = false;

    async function fetchBacktestRun() {
      setIsLoading(true);
      setError(null);

      try {
        // TODO: 실제 API 엔드포인트에 맞게 수정
        // 예시: /api/backtest-runs/{runId}
        const res = await fetch(`/api/backtest-runs/${runId}`, {
          method: 'GET',
          credentials: 'include',
        });

        if (!res.ok) {
          throw new Error(`Failed to fetch backtest run. status=${res.status}`);
        }

        const data = await res.json();

        if (isCancelled) return;

        setRawResponse(data);
        const mapped = mapBacktestApiToResultProps(data);
        setResultProps(mapped);
      } catch (err) {
        if (isCancelled) return;
        console.error(err);
        setError(err);
      } finally {
        if (!isCancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchBacktestRun();

    return () => {
      isCancelled = true;
    };
  }, [runId]);

  return {
    isLoading,
    error,
    resultProps,
    rawResponse,
  };
}
