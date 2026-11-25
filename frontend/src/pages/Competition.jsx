import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import styles from './Competition.module.css';
import CompetitionHome from '../components/competition/CompetitionHome';
import StockOrder from '../components/competition/StockOrder';
import { getCurrentCompetition } from '../utils/competitionApi';

// 탭은 주소(?tab=)에 남겨서 새로고침하거나 링크로 들어와도 같은 탭이 열린다.
const TABS = [
  { key: 'home', label: '대회 홈' },
  { key: 'stocks', label: '종목 · 주문' },
];

const Competition = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = TABS.some((t) => t.key === searchParams.get('tab'))
    ? searchParams.get('tab')
    : 'home';
  const [competition, setCompetition] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    getCurrentCompetition()
      .then(setCompetition)
      .catch((err) => setError(err));
  }, []);

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>모의투자 리그</h1>
        <nav aria-label="리그 메뉴" className={styles.tabs}>
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              aria-current={tab === t.key ? 'page' : undefined}
              className={`${styles.tab} ${tab === t.key ? styles.activeTab : ''}`}
              onClick={() => setSearchParams({ tab: t.key })}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>

      {error && (
        <p className={styles.notice}>
          {error.status === 404 ? '아직 열린 대회가 없어요.' : error.message}
        </p>
      )}
      {!error && !competition && <p className={styles.notice}>불러오는 중…</p>}
      {competition && tab === 'home' && (
        <CompetitionHome competition={competition} />
      )}
      {competition && tab === 'stocks' && (
        <StockOrder competition={competition} />
      )}
    </div>
  );
};

export default Competition;
