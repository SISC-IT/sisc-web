import { useState, useEffect } from 'react';
import { useImmer } from 'use-immer';
import { v4 as uuid } from 'uuid';

import styles from './AttendanceManage.module.css';

import SessionSettingCard from '../components/attendancemanage/SessionSettingCard';
import RoundAddingCard from '../components/attendancemanage/RoundAddingCard';
import AttendanceManagementCard from '../components/attendancemanage/AttendanceManagementCard';
import SessionManagementCard from '../components/attendancemanage/SessionManagementCard';
import { attendanceSessionApi, attendanceRoundApi, attendanceCheckInApi } from '../utils/attendanceApi';

const sessionData = [
  {
    id: 'session-1',
    title: '금융 IT팀 세션',
    rounds: [
      {
        id: 'round-1',
        date: '2025-11-06',
        startTime: '10:00:00',
        availableMinutes: 20,
        status: 'opened',
        participants: [
          { memberId: 'member-1', name: '김민준', attendance: '출석' },
          { memberId: 'member-2', name: '이서연', attendance: '결석' },
          { memberId: 'member-3', name: '박도윤', attendance: '출석' },
        ],
      },
      {
        id: 'round-2',
        date: '2025-11-06',
        startTime: '11:00:00',
        availableMinutes: 30,
        status: 'opened',
        participants: [
          { memberId: 'member-1', name: '김민준', attendance: '출석' },
          { memberId: 'member-2', name: '이서연', attendance: '출석' },
          { memberId: 'member-3', name: '박도윤', attendance: '결석' },
        ],
      },
    ],
  },
];

const AttendanceManage = () => {
  const [sessions, setSessions] = useImmer(sessionData);
  const [selectedSessionId, setSelectedSessionId] = useState(null);
  const [selectedRound, setSelectedRound] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // API에서 세션 데이터 로드
  useEffect(() => {
    const loadSessions = async () => {
      try {
        setLoading(true);
        const data = await attendanceSessionApi.getAllSessions();

        // API 응답이 배열인 경우
        if (Array.isArray(data)) {
          const sessionsWithRounds = await Promise.all(
            data.map(async (session) => {
              try {
                const rounds = await attendanceRoundApi.getRoundsBySession(session.attendanceSessionId);
                return {
                  id: session.attendanceSessionId,
                  title: session.title,
                  code: session.code,
                  rounds: Array.isArray(rounds) ? rounds.map(r => ({
                    id: r.roundId,
                    date: r.roundDate,
                    startTime: r.startTime,
                    availableMinutes: r.allowedMinutes,
                    status: r.roundStatus,
                    participants: [],
                  })) : [],
                };
              } catch (err) {
                console.error('라운드 로드 실패:', err);
                return {
                  id: session.attendanceSessionId,
                  title: session.title,
                  code: session.code,
                  rounds: [],
                };
              }
            })
          );
          setSessions(sessionsWithRounds);
        }
        setError(null);
      } catch (err) {
        console.error('세션 로드 실패:', err);
        // 에러 시 기본 데이터 유지
        setError('세션 데이터를 불러오지 못했습니다. 로컬 데이터를 사용합니다.');
      } finally {
        setLoading(false);
      }
    };

    loadSessions();
  }, [setSessions]);

  // 라운드 선택 시 출석 명단 로드
  useEffect(() => {
    if (!selectedRound) return;

    const loadAttendances = async () => {
      try {
        console.log('📋 라운드 출석 명단 조회 시작:', selectedRound);
        const attendances = await attendanceRoundApi.getAttendancesByRound(selectedRound);

        console.log('✅ 라운드 출석 명단 조회 성공:', attendances);

        // 세션 상태 업데이트: 해당 라운드의 participants를 설정
        setSessions((draft) => {
          const session = draft.find((s) => s.id === selectedSessionId);
          if (!session) return;

          const round = session.rounds.find((r) => r.id === selectedRound);
          if (!round) return;

          // API 응답을 participants 형식으로 변환
          round.participants = Array.isArray(attendances) ? attendances.map(a => ({
            attendanceId: a.attendanceId,  // 고유한 ID (모든 참가자에 대해 unique)
            memberId: a.userId || 'anonymous',
            name: a.userName,
            attendance: a.attendanceStatus === 'PRESENT' ? '출석' :
                       a.attendanceStatus === 'LATE' ? '지각' : '결석',
          })) : [];
        });
      } catch (err) {
        console.error('❌ 라운드 출석 명단 조회 실패:', err);
        setError('출석 명단을 불러오지 못했습니다.');
      }
    };

    loadAttendances();
  }, [selectedRound, selectedSessionId, setSessions]);

  const handleAttendanceChange = (memberId, newAttendance) => {
    setSessions((draft) => {
      const session = draft.find((s) => s.id === selectedSessionId);
      if (!session) return;

      const round = session.rounds.find((r) => r.id === selectedRound);
      if (!round) return;

      const participant = round.participants.find(
        (p) => p.memberId === memberId
      );
      if (!participant) return;

      // 통과 시 출석 상태 업데이트
      participant.attendance = newAttendance;
    });
  };

  const handleRoundAdded = (newRound) => {
    setSessions((draft) => {
      const session = draft.find((s) => s.id === selectedSessionId);
      if (!session) return;

      session.rounds.push(newRound);
    });
  };

  const handleAddSession = async (sessionTitle) => {
    try {
      // 세션만 생성 (시작 시간은 2시간 후로 설정 - 타임존 차이 고려)
      const futureTime = new Date(Date.now() + 2 * 60 * 60 * 1000);
      console.log('📋 출석세션 생성 시작:', {
        title: sessionTitle,
        startsAt: futureTime.toISOString(),
        windowSeconds: 1800,
      });

      const sessionResponse = await attendanceSessionApi.createSession({
        title: sessionTitle,
        startsAt: futureTime.toISOString(),
        windowSeconds: 1800,
      });

      console.log('✅ 출석세션 생성 성공:', {
        sessionId: sessionResponse.attendanceSessionId,
        code: sessionResponse.code,
        title: sessionResponse.title,
        startsAt: sessionResponse.startsAt,
      });

      const newSession = {
        id: sessionResponse.attendanceSessionId,
        title: sessionResponse.title,
        code: sessionResponse.code,
        rounds: [],
      };

      setSessions([...sessions, newSession]);
      setError(null);
      console.log('✅ 화면에 세션 추가 완료 (라운드는 별도로 추가)');
    } catch (err) {
      console.error('세션 추가 실패:', err);
      setError('세션 추가에 실패했습니다.');

      // 폴백: 로컬 데이터로도 추가 (세션만)
      const sessionId = `session-${uuid()}-${Math.random().toString(36)}`;
      const newSession = {
        id: sessionId,
        title: sessionTitle,
        code: Math.random().toString(36).substring(2, 8).toUpperCase(),
        rounds: [],
      };
      setSessions([...sessions, newSession]);
    }
  };

  const selectedSession = sessions.find(
    (session) => session.id === selectedSessionId
  );

  const selectedRoundData = selectedSession?.rounds.find(
    (round) => round.id === selectedRound
  );

  const participants = selectedRoundData?.participants || [];

  return (
    <div className={styles.attendanceManageContainer}>
      <div className={styles.mainTitle}>출석관리(담당자)</div>
      {loading && <div style={{ padding: '20px', textAlign: 'center' }}>세션 데이터 로드 중...</div>}
      {error && <div style={{ padding: '20px', color: 'orange', textAlign: 'center' }}>{error}</div>}
      <div className={styles.cardLayout}>
        <div className={styles.leftColumn}>
          <SessionSettingCard styles={styles} onAddSession={handleAddSession} />
          <RoundAddingCard
            sessions={sessions}
            selectedSessionId={selectedSessionId}
            onRoundAdded={handleRoundAdded}
          />
          <SessionManagementCard
            styles={styles}
            sessions={sessions}
            selectedSessionId={selectedSessionId}
            setSelectedSessionId={setSelectedSessionId}
            selectedRound={selectedRound}
            setSelectedRound={setSelectedRound}
          />
        </div>
        <AttendanceManagementCard
          styles={styles}
          selectedRound={selectedRound}
          onAttendanceChange={handleAttendanceChange}
          participants={participants}
        />
      </div>
    </div>
  );
};

export default AttendanceManage;
