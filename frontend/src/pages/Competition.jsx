import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import styles from './Competition.module.css';
import CompetitionHome from '../components/competition/CompetitionHome';
import StockOrder from '../components/competition/StockOrder';
import MyAccount from '../components/competition/MyAccount';
import Deposit from '../components/competition/Deposit';
import Rankings from '../components/competition/Rankings';
import { getCurrentCompetition } from '../utils/competitionApi';

// 탭은 주소(?tab=)에 남겨서 새로고침하거나 링크로 들어와도 같은 탭이 열린다.
const TABS = [
  { key: 'account', label: '내 계좌' },
  { key: 'stocks', label: '종목 · 주문' },
  { key: 'deposit', label: '예금' },
  { key: 'rankings', label: '순위' },
  { key: 'info', label: '대회 정보' },
];

// 내 계좌를 보는 탭. 참가자가 아니면 계좌가 없다(제외된 참가자는 볼 수 있다).
const ACCOUNT_TABS = ['account', 'deposit'];

const TabContent = ({ tab, competition }) => {
  const { competitionId, myStatus } = competition;
  if (ACCOUNT_TABS.includes(tab) && !myStatus) {
    return <p className={styles.notice}>대회 참가자만 볼 수 있어요.</p>;
  }
  switch (tab) {
    case 'account':
      return <MyAccount competitionId={competitionId} />;
    case 'stocks':
      return <StockOrder competition={competition} />;
    case 'deposit':
      return <Deposit competition={competition} />;
    case 'rankings':
      return <Rankings competitionId={competitionId} />;
    default:
      return <CompetitionHome competition={competition} />;
  }
};

const Competition = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [competition, setCompetition] = useState(null);
  // 참가자가 아니거나 시작 전이면 계좌에 볼 게 없어서 대회 정보로 연다
  const defaultTab =
    competition && (!competition.myStatus || competition.status === 'READY')
      ? 'info'
      : 'account';
  const tab = TABS.some((t) => t.key === searchParams.get('tab'))
    ? searchParams.get('tab')
    : defaultTab;
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
      {competition && <TabContent tab={tab} competition={competition} />}
    </div>
  );
};

export default Competition;
