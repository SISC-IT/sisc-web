import { useState } from 'react';
import { useImmer } from 'use-immer';
import { v4 as uuid } from 'uuid';

import styles from './AttendanceManage.module.css';

import SessionSettingCard from '../components/attendancemanage/SessionSettingCard';
import AttendanceManagementCard from '../components/attendancemanage/AttendanceManagementCard';
import SessionManagementCard from '../components/attendancemanage/SessionManagementCard';

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

  // const currentSession = useMemo(
  //   () => sessions.find((s) => s.id === selectedSessionId) || null,
  //   [sessions, selectedSessionId]
  // );
  // const currentParticipants = useMemo(() => {
  //   if (!currentSession) return [];

  //   const currentRound = currentSession.rounds.find((round) => {
  //     round.id === selectedRound;
  //   });

  //   return currentRound ? currentRound.participants : [];
  // }, [currentSession, selectedRound]);

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

  const handleAddSession = (sessionTitle, roundDetails) => {
    const sessionId = `session-${uuid()}-${Math.random().toString(36)}`;
    const roundId = `round-${uuid()}-${Math.random().toString(5)}`;

    const newSession = {
      id: sessionId,
      title: sessionTitle,
      rounds: [
        {
          id: roundId,
          date: new Date().toISOString().slice(0, 10),
          startTime: `${roundDetails.hh}:${roundDetails.mm}:${roundDetails.ss}`,
          availableMinutes: parseInt(roundDetails.availableTimeMm, 10),
          status: 'opened',
          participants: [],
        },
      ],
    };

    setSessions([...sessions, newSession]);
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
      <div className={styles.cardLayout}>
        <div className={styles.leftColumn}>
          <SessionSettingCard styles={styles} onAddSession={handleAddSession} />
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
