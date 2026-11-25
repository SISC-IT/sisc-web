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

  useEffect(() => {
    Promise.all([
      getRankings(competitionId),
      getTeamRankings(competitionId),
      getUserDetails(),
    ])
      .then(([p, t, user]) => {
        setPeople(p);
        setTeams(t);
        setMe(user);
      })
      .catch((err) => setError(err.message));
  }, [competitionId]);

  if (error) return <p className={styles.errorBox}>{error}</p>;
  if (!people) return <p className={styles.muted}>불러오는 중…</p>;
  if (!people.snapshotDate) {
    return (
      <p className={styles.muted}>
        아직 순위가 없어요. 거래일 16:00에 처음 매겨져요.
      </p>
    );
  }

  const myTeam = people.rankings.find((r) => r.userId === me.id)?.teamName;

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
        <span className={styles.muted}>
          {shortDate(people.snapshotDate)} 16:00 기준
        </span>
      </div>

      {view === 'person' ? (
        <div className={styles.tableBox}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col" className={styles.center}>
                  순위
                </th>
                <th scope="col" className={styles.left}>
                  이름
                </th>
                <th scope="col" className={styles.left}>
                  팀
                </th>
                <th scope="col">총자산</th>
                <th scope="col">수익률</th>
              </tr>
            </thead>
            <tbody>
              {people.rankings.map((r) => (
                <tr
                  key={r.userId}
                  className={r.userId === me.id ? styles.mine : undefined}
                >
                  <td className={`${styles.center} ${styles.strong}`}>
                    {r.rank}
                    <span className={styles.sub}>
                      {rankChangeText(r.rankChange)}
                    </span>
                  </td>
                  <td className={`${styles.left} ${styles.strong}`}>
                    {r.userName}
                  </td>
                  <td className={styles.left}>{r.teamName}</td>
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
      ) : (
        <>
          <div className={styles.tableBox}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col" className={styles.center}>
                    순위
                  </th>
                  <th scope="col" className={styles.left}>
                    팀
                  </th>
                  <th scope="col">인원</th>
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
                    <td className={`${styles.left} ${styles.strong}`}>
                      {t.teamName}
                    </td>
                    <td>{t.memberCount}명</td>
                    <td className={styles.strong}>{t.averageRank}등</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className={styles.muted}>
            팀원 개인 등수의 평균이 낮을수록 앞서요.
          </p>
        </>
      )}
    </div>
  );
};

export default Rankings;
