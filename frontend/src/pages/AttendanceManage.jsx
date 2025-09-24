import { useMemo, useState } from 'react';
import styles from './AttendanceManage.module.css';

import CreateSessionForm from '../components/attendancemanage/CreateSessionForm';
import SessionSelect from '../components/attendancemanage/SessionSelect';
import RemainingTime from '../components/attendancemanage/RemainingTime';
import RosterTable from '../components/attendancemanage/RosterTable';

import {
  STATUSES,
  uuid,
  makeInitialMockSessions,
  makeMockRoster,
  makeLargeMockRoster,
} from '../utils/attendancemanageUtils';

const AttendanceManage = () => {
  // 초기 세션은 한 번만 생성
  const initialSessions = useMemo(
    () => makeInitialMockSessions().sort((a, b) => b.createdAt - a.createdAt),
    []
  );

  // 세션 목록
  const [sessions, setSessions] = useState(initialSessions);

  // 세션별 명단 (초기 두 세션에 미리 생성)
  const [rosterMap, setRosterMap] = useState(() => {
    const init = {};
    for (const s of initialSessions) {
      init[s.id] =
        s.title === '금융 it 출석' ? makeLargeMockRoster() : makeMockRoster();
    }
    return init;
  });

  // 선택된 세션
  const [selectedId, setSelectedId] = useState(initialSessions[0]?.id || '');

  const currentSession = useMemo(
    () => sessions.find((s) => s.id === selectedId) || null,
    [sessions, selectedId]
  );

  const currentRoster = useMemo(
    () => (currentSession ? (rosterMap[currentSession.id] ?? []) : []),
    [currentSession, rosterMap]
  );

  const sessionLabel = (s) => `${s.title}`;

  const onChangeStatus = (memberId, status) => {
    if (!currentSession) return;
    setRosterMap((prev) => ({
      ...prev,
      [currentSession.id]: (prev[currentSession.id] ?? []).map((m) =>
        m.id === memberId ? { ...m, status } : m
      ),
    }));
  };

  const handleCreateSession = ({ title, code, durationSec }) => {
    const now = Date.now();
    const newSession = {
      id: uuid(),
      title,
      code,
      createdAt: now,
      expiresAt: now + durationSec * 1000,
    };

    setSessions((prev) => [newSession, ...prev]);
    setRosterMap((prev) => ({ ...prev, [newSession.id]: makeMockRoster() }));
    setSelectedId(newSession.id);
  };

  return (
    <div className={styles.page}>
      <h1 className={styles.pageTitle}>출석관리(관리자)</h1>

      <div className={styles.grid}>
        {/* 왼쪽: 세션 생성 */}
        <CreateSessionForm styles={styles} onCreate={handleCreateSession} />

        {/* 오른쪽: 출석 관리 */}
        <section className={styles.card}>
          <div className={styles.cardHeader}>
            <div className={styles.cardTitle}>출석 관리</div>
          </div>

          <SessionSelect
            styles={styles}
            sessions={sessions}
            selectedId={selectedId}
            onChange={setSelectedId}
            sessionLabel={sessionLabel}
          />

          {currentSession && (
            <RemainingTime
              styles={styles}
              expiresAt={currentSession.expiresAt}
            />
          )}

          <RosterTable
            styles={styles}
            roster={currentRoster}
            statuses={STATUSES}
            onChangeStatus={onChangeStatus}
          />
        </section>
      </div>
    </div>
  );
};

export default AttendanceManage;
