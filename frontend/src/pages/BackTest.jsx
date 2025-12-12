import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './BackTest.module.css';
import StrategyInfoCard from '../components/backtest/StrategyInfoCard';
import StocksCard from '../components/backtest/StocksCard';
import EntryRulesCard from '../components/backtest/EntryRulesCard';
import ExitRulesCard from '../components/backtest/ExitRulesCard';
import NotesCard from '../components/backtest/NotesCard';
import { api } from '../utils/axios';
import { toast } from 'react-toastify';

const BackTest = () => {
  const [strategyName, setStrategyName] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [initialCapital, setInitialCapital] = useState('');
  const [tickers, setTickers] = useState([]);
  const [defaultExitDays, setDefaultExitDays] = useState(0);
  const [entryRules, setEntryRules] = useState([]);
  const [exitRules, setExitRules] = useState([]);
  const [note, setNote] = useState('');

  const navigate = useNavigate();

  const handleRunBacktest = async () => {
    if (tickers.length === 0) {
      toast.error('하나 이상의 주식을 추가해주세요.');
      return;
    }

    if (entryRules.length === 0 || exitRules.length === 0) {
      toast.error('매수 및 매도 조건을 하나 이상 추가해주세요.');
      return;
    }

    const requests = tickers.map((ticker) => ({
      title: strategyName
        ? `${strategyName} (${ticker})`
        : `백테스트 (${ticker})`,
      startDate: startDate || null,
      endDate: endDate || null,
      templateId: null,
      strategy: {
        initialCapital: initialCapital === '' ? null : Number(initialCapital),
        ticker,
        defaultExitDays,
        buyConditions: entryRules,
        sellConditions: exitRules,
        note,
      },
    }));

    try {
      const results = await Promise.all(
        requests.map(async (body) => {
          const response = await api.post('/api/backtest/runs', body);
          return response.data ?? null;
        })
      );

      const firstResult = results[0];

      if (!firstResult) {
        toast.error('실행 결과를 받지 못했습니다.');
        return;
      }

      // 결과 페이지로 이동 + state로 결과 전달
      navigate('/backtest/result', {
        state: {
          result: firstResult,
        },
      });
      toast.success('백테스트 실행을 요청했습니다.');
    } catch (error) {
      console.error('Error running backtest:', error);

      const message =
        error?.message ||
        error?.data?.message ||
        '백테스트 실행 중 알 수 없는 오류가 발생했습니다.';

      toast.error(`백테스트 실행 중 오류가 발생했습니다: ${message}`);
    }
  };

  return (
    <div className={styles.container}>
      <h1 className={styles.pageTitle}>백테스팅</h1>

      <StrategyInfoCard
        strategyName={strategyName}
        setStrategyName={setStrategyName}
        startDate={startDate}
        setStartDate={setStartDate}
        endDate={endDate}
        setEndDate={setEndDate}
        initialCapital={initialCapital}
        setInitialCapital={setInitialCapital}
        onRunBacktest={handleRunBacktest}
      />
      <StocksCard tickers={tickers} setTickers={setTickers} />
      <EntryRulesCard rules={entryRules} setRules={setEntryRules} />
      <ExitRulesCard
        rules={exitRules}
        setRules={setExitRules}
        defaultExitDays={defaultExitDays}
        setDefaultExitDays={setDefaultExitDays}
      />
      <NotesCard note={note} setNote={setNote} />
    </div>
  );
};

export default BackTest;
