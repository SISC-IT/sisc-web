import { useState } from 'react';
import styles from './AttendanceManage.module.css';

import SessionSettingCard from '../components/attendancemanage/SessionSettingCard';
import AttendanceManagementCard from '../components/attendancemanage/AttendanceManagementCard';
import SessionManagementCard from '../components/attendancemanage/SessionManagementCard';

const sessionData = [
  {
    id: 'session-1',
    title: '금융 IT팀 세션',
    round: [
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
  const [sessions, setSessions] = useState(sessionData);
  const [selectedSessionId, setSelectedSessionId] = useState(null);
  const [selectedRound, setSelectedRound] = useState(null);

  const handleAttendanceChange = (memberId, newAttendance) => {
    const newSessions = sessions.map((session) => {
      if (session.id === selectedSessionId) {
        const newRounds = session.round.map((round) => {
          if (round.id === selectedRound) {
            const newParticipants = round.participants.map((participant) => {
              if (participant.memberId === memberId) {
                return { ...participant, attendance: newAttendance };
              }
              return participant;
            });
            return { ...round, participants: newParticipants };
          }
          return round;
        });
        return { ...session, round: newRounds };
      }
      return session;
    });
    setSessions(newSessions);
  };

  const handleAddSession = (sessionTitle, roundDetails) => {
    const newSession = {
      id: `session-${Date.now()}`,
      title: sessionTitle,
      round: [
        {
          id: `round-${Date.now()}`,
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

  return (
    <div className={styles.attendanceManageContainer}>
      <div className={styles.mainTitle}>출석관리(담당자)</div>
      <div className={styles.cardLayout}>
        <div className={styles.leftColumn}>
          <SessionSettingCard
            className={styles.settings}
            styles={styles}
            onAddSession={handleAddSession}
          />
          <SessionManagementCard
            className={styles.session}
            styles={styles}
            sessions={sessions}
            selectedSessionId={selectedSessionId}
            setSelectedSessionId={setSelectedSessionId}
            selectedRound={selectedRound}
            setSelectedRound={setSelectedRound}
          />
        </div>
        <AttendanceManagementCard
          className={styles.roster}
          styles={styles}
          sessions={sessions}
          selectedSessionId={selectedSessionId}
          selectedRound={selectedRound}
          onAttendanceChange={handleAttendanceChange}
        />
      </div>
    </div>
  );
};

export default AttendanceManage;
