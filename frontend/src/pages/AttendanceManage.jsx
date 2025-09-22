import { useState, useMemo, useEffect } from 'react';
import styles from './AttendanceManage.module.css';

const onlyDigits = (s) => s.replace(/\D/g, '');
const STATUSES = ['출석', '지각', '결석'];

// mok데이터
const uuid = () =>
  globalThis.crypto && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

function randomStatus() {
  // 출석 70%, 지각 20%, 결석 10% 느낌으로 랜덤
  const r = Math.random();
  if (r < 0.7) return '출석';
  if (r < 0.9) return '지각';
  return '결석';
}

function makeInitialMockSessions() {
  const now = Date.now();
  const s1 = {
    id: uuid(),
    title: '전체 출석',
    code: '1234',
    createdAt: now - 30 * 60 * 1000,
    expiresAt: now + 15 * 60 * 1000, // 15분 남음(활성)
  };
  const s2 = {
    id: uuid(),
    title: '금융 it 출석',
    code: '5678',
    createdAt: now - 120 * 60 * 1000,
    expiresAt: now - 60 * 1000, // 만료
  };
  return [s1, s2];
}

function makeMockRoster() {
  // 새 세션 생성 시에도 이 이름들로 랜덤 상태 배정
  const names = ['안강준', '김동은', '황순영'];
  return names.map((name) => ({
    id: uuid(),
    name,
    status: randomStatus(),
  }));
}

function makeLargeMockRoster() {
  const names = [
    '안강준',
    '김동은',
    '황순영',
    '이민준',
    '박서준',
    '김도윤',
    '최지호',
    '윤하준',
    '강시우',
    '조은우',
    '신유준',
    '한이안',
    '정지훈',
    '송주원',
    '오건우',
    '임도현',
    '장선우',
    '서예준',
    '황지후',
    '문준서',
  ];
  return names.map((name) => ({
    id: uuid(),
    name,
    status: randomStatus(),
  }));
}

// ---------------

const AttendanceManage = () => {
  const initialSessions = useMemo(
    () => makeInitialMockSessions().sort((a, b) => b.createdAt - a.createdAt),
    []
  );

  // 세션 목록
  const [sessions, setSessions] = useState(initialSessions);
  // 세션별 명단
  const [rosterMap, setRosterMap] = useState(() => {
    const init = {};
    for (const s of initialSessions) {
      if (s.title === '금융 it 출석') {
        init[s.id] = makeLargeMockRoster();
      } else {
        init[s.id] = makeMockRoster();
      }
    }
    return init;
  });
  // 선택된 세션 ID
  const [selectedId, setSelectedId] = useState(
    (makeInitialMockSessions()[0] && initialSessions[0].id) || ''
  );

  // --------------------
  const [title, setTitle] = useState(''); // 세션 제목
  const [code, setCode] = useState(''); // 세션 번호
  const [hh, setHh] = useState(''); // 시
  const [mm, setMm] = useState(''); // 분
  const [ss, setSs] = useState(''); // 초

  // 이벤트 핸들러
  // 세션 생성
  const createSession = (e) => {
    e.preventDefault();

    if (!title.trim() || !code || durationSec <= 0) {
      alert('세션 제목, 번호, 시간을 모두 입력해주세요.');
      return;
    }

    const now = Date.now();
    const newSession = {
      id: uuid(),
      title: title.trim(),
      code,
      createdAt: now,
      expiresAt: now + durationSec * 1000,
    };
    setSessions((prev) => [newSession, ...prev]);

    setRosterMap((prev) => ({ ...prev, [newSession.id]: makeMockRoster() }));
    setSelectedId(newSession.id);

    // 폼 초기화
    setTitle('');
    setCode('');
    setHh('');
    setMm('');
    setSs('');
  };

  // 현재 선택된 세션과 명단
  const currentSession = useMemo(
    () => sessions.find((s) => s.id === selectedId) || null,
    [sessions, selectedId]
  );
  const currentRoster = useMemo(
    () => (currentSession ? (rosterMap[currentSession.id] ?? []) : []),
    [currentSession, rosterMap]
  );

  const [remainingTime, setRemainingTime] = useState(0);

  useEffect(() => {
    if (!currentSession) {
      setRemainingTime(0);
      return;
    }

    const calculateRemainingTime = () => {
      const now = Date.now();
      const remaining = Math.max(0, currentSession.expiresAt - now);
      setRemainingTime(remaining);
    };

    calculateRemainingTime();
    const intervalId = setInterval(calculateRemainingTime, 1000);

    return () => clearInterval(intervalId);
  }, [currentSession]);

  const formatTime = (ms) => {
    if (ms <= 0) {
      return '00:00:00';
    }
    const totalSeconds = Math.floor(ms / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(
      2,
      '0'
    )}:${String(seconds).padStart(2, '0')}`;
  };

  const sessionLabel = (s) => {
    return `${s.title}`;
  };
  const changeStatus = (memberId, status) => {
    if (!currentSession) return;
    setRosterMap((prev) => ({
      ...prev,
      [currentSession.id]: (prev[currentSession.id] ?? []).map((m) =>
        m.id === memberId ? { ...m, status } : m
      ),
    }));
  };

  const durationSec = useMemo(() => {
    const H = parseInt(hh || '0', 10) || 0;
    const M = parseInt(mm || '0', 10) || 0;
    const S = parseInt(ss || '0', 10) || 0;
    return Math.max(0, H * 3600 + M * 60 + S);
  }, [hh, mm, ss]);

  return (
    <div className={styles.page}>
      {/* 페이지 타이틀 */}
      <h1 className={styles.pageTitle}>출석관리(관리자)</h1>
      <div className={styles.grid}>
        {/* 왼쪽 카드 - 세션 생성 */}
        <section className={styles.card}>
          <div className={styles.cardHeader}>
            <div className={styles.cardTitle}>세션 설정</div>
          </div>

          <form className={styles.form} onSubmit={createSession}>
            <div className={styles.field}>
              <label>세션 제목</label>
              <input
                className={styles.input}
                placeholder="세션 제목"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>
            <div className={styles.field}>
              <label>세션 번호</label>
              <input
                className={styles.input}
                placeholder="세션 번호"
                value={code}
                onChange={(e) => setCode(e.target.value)}
              />
            </div>
            <div className={styles.field}>
              <label>세션 시간</label>
              <div className={styles.timefield}>
                {/* <label>시(HH)</label> */}
                <input
                  className={styles.input}
                  placeholder="시(HH)"
                  value={hh}
                  inputMode="numeric"
                  onChange={(e) =>
                    setHh(onlyDigits(e.target.value).slice(0, 2))
                  }
                />
                {/* <label>분(MM)</label> */}
                <input
                  className={styles.input}
                  placeholder="분(MM)"
                  value={mm}
                  inputMode="numeric"
                  onChange={(e) =>
                    setMm(onlyDigits(e.target.value).slice(0, 2))
                  }
                />
                {/* <label>초(SS)</label> */}
                <input
                  className={styles.input}
                  placeholder="초(SS)"
                  value={ss}
                  inputMode="numeric"
                  onChange={(e) =>
                    setSs(onlyDigits(e.target.value).slice(0, 2))
                  }
                />
              </div>
            </div>
            <button className={styles.btn}>생성</button>
          </form>
        </section>
        {/* 오른쪽 카드 - 출석 관리 */}
        <section className={styles.card}>
          <div className={styles.cardHeader}>
            <div className={styles.cardTitle}>출석 관리</div>
          </div>

          <div className={styles.field}>
            <label className={styles.srOnly}>세션선택</label>
            <select
              className={styles.select}
              value={selectedId}
              onChange={(e) => setSelectedId(e.target.value)}
            >
              {sessions
                .slice()
                .sort((a, b) => b.createdAt - a.createdAt)
                .map((s) => (
                  <option key={s.id} value={s.id}>
                    {sessionLabel(s)}
                  </option>
                ))}
            </select>
          </div>

          {currentSession && (
            <div className={styles.remainingTime}>
              남은 시간: {formatTime(remainingTime)}
            </div>
          )}

          <div className={styles.table}>
            <div className={styles.names}>
              <div>이름</div>
              <div>상태</div>
              <div>변경</div>
            </div>
            <div className={styles.rosterScroll}>
              {currentRoster.map((m) => (
                <div className={styles.trow} key={m.id}>
                  <div className={styles.tcell}>{m.name}</div>
                  <div className={`${styles.tcell} ${styles.muted}`}>
                    {m.status}
                  </div>
                  <div className={styles.tcell}>
                    <select
                      className={`${styles.select} ${styles.compact}`}
                      value={m.status}
                      onChange={(e) => changeStatus(m.id, e.target.value)}
                    >
                      {STATUSES.map((st) => (
                        <option key={st} value={st}>
                          {st}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};

export default AttendanceManage;
