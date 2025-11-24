import { useState } from 'react';
import styles from './BackTest.module.css';
import StrategyInfoCard from '../components/backtest/StrategyInfoCard';
import StocksCard from '../components/backtest/StocksCard';
import EntryRulesCard from '../components/backtest/EntryRulesCard';
import ExitRulesCard from '../components/backtest/ExitRulesCard';
import NotesCard from '../components/backtest/NotesCard';

const BASE_URL = import.meta.env.VITE_API_URL;

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

  const handleRunBacktest = async () => {
    if (tickers.length === 0) {
      alert('하나 이상의 주식을 추가해주세요.');
      return;
    }

    const accessToken = localStorage.getItem('accessToken');

    const requests = tickers.map((ticker) => ({
      title: strategyName
        ? `${strategyName} (${ticker})`
        : `백테스트 (${ticker})`,
      startDate: startDate || null,
      endDate: endDate || null,
      templateId: null,
      strategy: {
        initialCapital,
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
          const response = await fetch(`${BASE_URL}/api/backtest/runs`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              ...(accessToken
                ? { Authorization: `Bearer ${accessToken}` }
                : {}),
            },
            body: JSON.stringify(body),
          });

          if (!response.ok) {
            const errorText = await response.text();
            throw new Error(
              errorText || `HTTP error! status: ${response.status}`
            );
          }

          return response.json().catch(() => null);
        })
      );

      console.log('Backtest run created successfully:', results);
      alert('백테스트 실행을 요청했습니다.');
    } catch (error) {
      console.error('Error running backtest:', error);
      alert(`백테스트 실행 중 오류가 발생했습니다: ${error.message}`);
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
