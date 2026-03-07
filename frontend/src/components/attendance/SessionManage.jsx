import { useMemo } from 'react';
import styles from './SessionManage.module.css';
import { ClipboardCheck } from 'lucide-react';

const normalizeSessionTitle = (sessionTitle) =>
  typeof sessionTitle === 'string' && sessionTitle.trim() !== '' ? sessionTitle.trim() : '기타';

const getRoundKey = (session) => session.roundId || `${session.roundDate || ''}-${session.roundStartAt || ''}`;

const getTimestamp = (session) => {
  const dateSource = session.roundStartAt || session.roundDate || session.createdAt || session.checkedAt;
  const timestamp = Date.parse(dateSource);
  return Number.isNaN(timestamp) ? 0 : timestamp;
};

const formatDate = (dateValue) => {
  if (!dateValue) return '-';
  const date = new Date(dateValue);
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString();
};

const formatTime = (dateValue) => {
  if (!dateValue) return '-';
  const date = new Date(dateValue);
  return Number.isNaN(date.getTime())
    ? '-'
    : date.toLocaleTimeString('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      });
};

const formatAttendanceStatus = (attendanceStatus) => {
  const status = (attendanceStatus || '').toUpperCase();

  if (status === 'PRESENT' || status === 'ATTENDED') return '출석';
  if (status === 'LATE') return '지각';
  if (status === 'ABSENT') return '결석';
  if (status === 'EXCUSED') return '공결';
  if (status === 'PENDING') return '대기';

  return attendanceStatus || '-';
};

const SessionManage = ({ sessions = [], selectedSession = '', loading, error }) => {
  const roundIndexMapBySession = useMemo(() => {
    const roundMapBySession = new Map();
    sessions.forEach((session) => {
      const sessionTitle = normalizeSessionTitle(session.sessionTitle);
      if (!roundMapBySession.has(sessionTitle)) {
        roundMapBySession.set(sessionTitle, new Map());
      }

      const roundMap = roundMapBySession.get(sessionTitle);
      const roundKey = getRoundKey(session);
      if (!roundMap.has(roundKey)) {
        roundMap.set(roundKey, getTimestamp(session));
      }
    });

    const indexedMapBySession = new Map();

    roundMapBySession.forEach((roundMap, sessionTitle) => {
      const sortedRoundKeys = Array.from(roundMap.entries())
        .sort((a, b) => a[1] - b[1])
        .map(([roundKey]) => roundKey);

      const indexedRoundMap = new Map();
      sortedRoundKeys.forEach((roundKey, index) => {
        indexedRoundMap.set(roundKey, index + 1);
      });

      indexedMapBySession.set(sessionTitle, indexedRoundMap);
    });

    return indexedMapBySession;
  }, [sessions]);

  const visibleSessions = useMemo(() => {
    const selectedSessionTitle = normalizeSessionTitle(selectedSession);

    const filtered = selectedSession
      ? sessions.filter((session) => normalizeSessionTitle(session.sessionTitle) === selectedSessionTitle)
      : sessions;

    const deduplicated = filtered.filter((session, index, array) => {
      if (!session?.attendanceId) return true;
      return array.findIndex((item) => item?.attendanceId === session.attendanceId) === index;
    });

    return [...deduplicated].sort((a, b) => getTimestamp(a) - getTimestamp(b));
  }, [sessions, selectedSession]);

  if (error) return <div>{error}</div>;

  const rows = loading ? [] : visibleSessions;

  return (
    <div className={styles.card}>
      <div className={styles.title}>
        <ClipboardCheck />
        출석 목록
      </div>

      <table className={styles.table} role="grid">
        <thead>
          <tr>
            <th>일자</th>
            <th>출석시작시간</th>
            <th>회차</th>
            <th>이름</th>
            <th>출석 상태</th>
          </tr>
        </thead>

        <tbody>
          {rows.map((s) => {
            const sessionTitle = normalizeSessionTitle(s.sessionTitle);
            const roundKey = getRoundKey(s);
            const roundIndex = roundIndexMapBySession.get(sessionTitle)?.get(roundKey) ?? '-';

            return (
            <tr key={s.attendanceId}>
              <td>{formatDate(s.roundDate)}</td>
              <td>{formatTime(s.roundStartAt)}</td>
              <td>{roundIndex}</td>
              <td>{s.userName}</td>
              <td>{formatAttendanceStatus(s.attendanceStatus)}</td>
            </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default SessionManage;