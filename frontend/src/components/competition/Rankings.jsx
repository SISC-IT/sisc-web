import { useEffect, useState } from 'react';
import styles from './Account.module.css';
import { getRankings, getTeamRankings } from '../../utils/competitionApi';
import { getUserDetails } from '../../utils/userApi';
import {
  changeColor,
  shortDate,
  signedPercent,
  won,
} from '../../utils/competitionFormat';

const rankChangeText = (change) => {
  if (change === null || change === undefined || change === 0) return '';
  return change > 0 ? `▲${change}` : `▼${-change}`;
};

const Rankings = ({ competitionId }) => {
  const [view, setView] = useState('person');
  const [people, setPeople] = useState(null);
  const [teams, setTeams] = useState(null);
  const [me, setMe] = useState(null);
  const [error, setError] = useState('');
  const [teamError, setTeamError] = useState('');

  useEffect(() => {
    // 하나가 실패해도 나머지는 보여 준다. 내 정보가 없으면 내 행만 표시하지 않는다.
    getRankings(competitionId)
      .then(setPeople)
      .catch((err) => setError(err.message));
    getTeamRankings(competitionId)
      .then(setTeams)
      .catch((err) => setTeamError(err.message));
    getUserDetails()
      .then(setMe)
      .catch(() => {});
  }, [competitionId]);

  const myTeam = people?.rankings.find((r) => r.userId === me?.id)?.teamName;
  const snapshotDate = people?.snapshotDate ?? teams?.snapshotDate;
  const NO_RANKING = (
    <p className={styles.muted}>
      아직 순위가 없어요. 거래일 16:00에 처음 매겨져요.
    </p>
  );

  return (
    <div className={styles.panel}>
      <div className={styles.sectionHead}>
        <div role="group" aria-label="순위 종류" className={styles.chips}>
          <button
            type="button"
            aria-pressed={view === 'person'}
            className={`${styles.chip} ${view === 'person' ? styles.chipOn : ''}`}
            onClick={() => setView('person')}
          >
            개인 순위
          </button>
          <button
            type="button"
            aria-pressed={view === 'team'}
            className={`${styles.chip} ${view === 'team' ? styles.chipOn : ''}`}
            onClick={() => setView('team')}
          >
            팀 순위
          </button>
        </div>
        {snapshotDate && (
          <span className={styles.muted}>
            {shortDate(snapshotDate)} 16:00 기준
          </span>
        )}
      </div>

      {view === 'person' ? (
        error ? (
          <p className={styles.errorBox}>{error}</p>
        ) : !people ? (
          <p className={styles.muted}>불러오는 중…</p>
        ) : !people.snapshotDate ? (
          NO_RANKING
        ) : (
          <div className={styles.tableBox}>
            <table className={styles.table}>
              <colgroup>
                <col style={{ width: '12%' }} />
                <col />
                <col style={{ width: '22%' }} />
                <col style={{ width: '18%' }} />
              </colgroup>
              <thead>
                <tr>
                  <th scope="col" className={styles.center}>
                    순위
                  </th>
                  <th scope="col" className={styles.left}>
                    이름
                  </th>
                  <th scope="col">총자산</th>
                  <th scope="col">수익률</th>
                </tr>
              </thead>
              <tbody>
                {people.rankings.map((r) => (
                  <tr
                    key={r.userId}
                    className={r.userId === me?.id ? styles.mine : undefined}
                  >
                    <td className={`${styles.center} ${styles.strong}`}>
                      {r.rank}
                      <span className={styles.sub}>
                        {rankChangeText(r.rankChange)}
                      </span>
                    </td>
                    <td className={styles.left}>
                      <span className={styles.strong}>{r.userName}</span>
                      <span className={styles.sub}>{r.teamName}</span>
                    </td>
                    <td>{won(r.totalValue)}</td>
                    <td
                      className={styles.strong}
                      style={{ color: changeColor(r.profitRate) }}
                    >
                      {signedPercent(r.profitRate)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : teamError ? (
        <p className={styles.errorBox}>{teamError}</p>
      ) : !teams ? (
        <p className={styles.muted}>불러오는 중…</p>
      ) : !teams.snapshotDate ? (
        NO_RANKING
      ) : (
        <>
          <div className={styles.tableBox}>
            <table className={styles.table}>
              <colgroup>
                <col style={{ width: '12%' }} />
                <col />
                <col style={{ width: '22%' }} />
              </colgroup>
              <thead>
                <tr>
                  <th scope="col" className={styles.center}>
                    순위
                  </th>
                  <th scope="col" className={styles.left}>
                    팀
                  </th>
                  <th scope="col">평균 등수</th>
                </tr>
              </thead>
              <tbody>
                {teams.rankings.map((t) => (
                  <tr
                    key={t.teamName}
                    className={t.teamName === myTeam ? styles.mine : undefined}
                  >
                    <td className={`${styles.center} ${styles.strong}`}>
                      {t.rank}
                    </td>
                    <td className={styles.left}>
                      <span className={styles.strong}>{t.teamName}</span>
                      <span className={styles.sub}>{t.memberCount}명</span>
                    </td>
                    <td className={styles.strong}>{t.averageRank}등</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className={styles.muted}>팀원들의 평균 등수로 순위를 매겨요.</p>
        </>
      )}
    </div>
  );
};

export default Rankings;
