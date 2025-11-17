import { createContext, useContext, useState } from 'react';
import { useImmer } from 'use-immer';
import { v4 as uuid } from 'uuid';

const AttendanceContext = createContext(null);

// 세션 목 데이터
const sessionData = [
  {
    id: 'session-1',
    title: '금융 IT팀 세션',
    // 세션의 기본 위치 정보
    location: {
      lat: 37.5499,
      lng: 127.0751,
    },
    defaultStartTime: '18:30:00', // 세션의 기본 시간 설정
    defaultAvailableMinutes: 30, // 출석 인정 시간 (분 단위)
    rewardPoints: 100, // 세션의 리워드
    isVisible: true, // 세션 공개 여부
    // 세션 회차들
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

export const AttendanceProvider = ({ children }) => {
  const [sessions, setSessions] = useImmer(sessionData);
  const [selectedSessionId, setSelectedSessionId] = useState(null);
  const [selectedRound, setSelectedRound] = useState(null);
  const [editingRound, setEditingRound] = useState(null);
  const [isRoundModifyModalOpen, setRoundModifyModalOpen] = useState(false);
  const [editingSession, setEditingSession] = useState(null);
  const [isSessionModifyModalOpen, setSessionModifyModalOpen] = useState(false);

  const [isAddRoundsModalOpen, setAddRoundsModalOpen] = useState(false);

  const handleAttendanceChange = (memberId, newAttendance) => {
    setSessions((draft) => {
      const session = draft.find((s) => s.id === selectedSessionId);
      if (!session) return;
      const round = session.rounds.find((r) => r.id === selectedRound);
      if (!round) return;
      const participant = round.participants.find(
        (p) => p.memberId === memberId
      );
      if (participant) {
        participant.attendance = newAttendance;
      }
    });
  };

  const handleRoundChange = (updateRoundData) => {
    setSessions((draft) => {
      const session = draft.find((s) => s.id === selectedSessionId);
      if (!session) return;
      const round = session.rounds.find((r) => r.id === updateRoundData.id);
      if (round) {
        round.startTime = updateRoundData.startTime;
        round.availableMinutes = updateRoundData.availableMinutes;
      }
      // 회차 수정 후 정렬
      if (session.rounds) {
        session.rounds.sort(
          (a, b) =>
            new Date(`${a.date}T${a.startTime}`) -
            new Date(`${b.date}T${b.startTime}`)
        );
      }
    });
  };

  const handleSessionChange = (updateSessionData) => {
    setSessions((draft) => {
      const session = draft.find((s) => s.id === updateSessionData.id);
      if (session) {
        session.defaultStartTime = updateSessionData.defaultStartTime;
        session.defaultAvailableMinutes =
          updateSessionData.defaultAvailableMinutes;
      }
    });
  };

  const openRoundModifyModal = () => setRoundModifyModalOpen(true);
  const closeRoundModifyModal = () => setRoundModifyModalOpen(false);

  const openSessionModifyModal = () => setSessionModifyModalOpen(true);
  const closeSessionModifyModal = () => setSessionModifyModalOpen(false);

  const openAddRoundsModal = () => setAddRoundsModalOpen(true);

  const closeAddRoundsModal = () => setAddRoundsModalOpen(false);

  const handleAddSession = (sessionTitle, roundDetails) => {
    const newSession = {
      id: `session-${uuid()}`,
      title: sessionTitle,
      defaultStartTime: `${roundDetails.hh}:${roundDetails.mm}:${roundDetails.ss}`,
      defaultAvailableMinutes: parseInt(roundDetails.availableTimeMm, 10),
      rewardPoints: 100,
      isVisible: true,
      rounds: [
        // {
        //   id: `round-${uuid()}`,
        //   date: new Date().toISOString().slice(0, 10),
        //   startTime: `${roundDetails.hh}:${roundDetails.mm}:${roundDetails.ss}`,
        //   availableMinutes: parseInt(roundDetails.availableTimeMm, 10),
        //   status: 'opened',
        //   participants: [],
        // },
      ],
    };
    setSessions((draft) => {
      draft.push(newSession);
    });
  };
  const handleAddRounds = (sessionId, newRounds) => {
    setSessions((draft) => {
      const session = draft.find((session) => session.id === sessionId);

      if (session) {
        session.rounds.push(...newRounds);
        // 회차 추가 후 정렬
        session.rounds.sort(
          (a, b) =>
            new Date(`${a.date}T${a.startTime}`) -
            new Date(`${b.date}T${b.startTime}`)
        );
      }
    });
  };

  const handleDeleteSession = (sessionId) => {
    setSessions((draft) => {
      const sessionIndex = draft.findIndex((session) => {
        return session.id === sessionId;
      });
      if (sessionIndex !== -1) {
        draft.splice(sessionIndex, 1);
      }
    });

    // 세션 선택 초기화
    setSelectedSessionId(null);
    setSelectedRound(null);
  };
  const handleDeleteRound = (roundId) => {
    setSessions((draft) => {
      const session = draft.find((session) => {
        return session.id === selectedSessionId;
      });

      if (session) {
        const roundIndex = session.rounds.findIndex(
          (round) => round.id === roundId
        );
        if (roundIndex !== -1) {
          session.rounds.splice(roundIndex, 1);
        }
      }
    });

    // 회차 선택 초기화
    setSelectedRound(null);
  };

  const selectedSession = sessions.find(
    (session) => session.id === selectedSessionId
  );

  const selectedRoundData = selectedSession?.rounds.find(
    (round) => round.id === selectedRound
  );

  const participants = selectedRoundData?.participants || [];

  // 공유할 값들을 객체로 묶기
  const value = {
    sessions,
    selectedSessionId,
    setSelectedSessionId,
    selectedRound,
    setSelectedRound,
    editingRound,
    setEditingRound,
    isRoundModifyModalOpen,
    openRoundModifyModal,
    closeRoundModifyModal,
    editingSession,
    setEditingSession,
    isSessionModifyModalOpen,
    openSessionModifyModal,
    closeSessionModifyModal,
    handleAttendanceChange,
    handleRoundChange,
    handleSessionChange,
    handleAddSession,
    handleAddRounds,
    participants,
    handleDeleteSession,
    handleDeleteRound,
    isAddRoundsModalOpen,
    openAddRoundsModal,
    closeAddRoundsModal,
  };

  return (
    <AttendanceContext.Provider value={value}>
      {children}
    </AttendanceContext.Provider>
  );
};

// 커스텀 훅
export const useAttendance = () => {
  const context = useContext(AttendanceContext);
  if (context === null) {
    throw new Error();
  }
  return context;
};
