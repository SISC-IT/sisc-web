import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react';
import { useImmer } from 'use-immer';
import {
  addRound,
  addUser,
  changeRoundData,
  changeSessionData,
  changeUserAttendance,
  createAttendanceSession,
  deleteRound,
  deleteSession,
  getAttendanceSessions,
  getRounds,
  addManager,
  deleteManager,
  deleteUser,
} from '../utils/attendanceManage';

export const AttendanceContext = createContext(null);

export const AttendanceProvider = ({ children }) => {
  const [sessions, setSessions] = useImmer([]);
  const [selectedSessionId, setSelectedSessionId] = useState(null);
  const [selectedRound, setSelectedRound] = useState(null);
  const [editingRound, setEditingRound] = useState(null);
  const [isRoundModifyModalOpen, setRoundModifyModalOpen] = useState(false);
  const [editingSession, setEditingSession] = useState(null);

  const [isSessionModifyModalOpen, setSessionModifyModalOpen] = useState(false);
  const [isAddRoundsModalOpen, setAddRoundsModalOpen] = useState(false);
  const [isAddUsersModalOpen, setAddUsersModalOpen] = useState(false);

  const [roundsVersion, setRoundsVersion] = useState(0);
  const [roundAttendanceVersion, setRoundAttendanceVersion] = useState(0);

  // 최초, setSessions가 호출될때마다 모든 세션 불러오기
  const fetchSessions = useCallback(async () => {
    try {
      const res = await getAttendanceSessions();
      setSessions(res || []);
    } catch (error) {
      console.error('모든 세션 데이터를 가져오는 데 실패했습니다: ', error);
      setSessions([]);
    }
  }, [setSessions]);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  const handleAttendanceChange = async (userId, roundId, newStatus) => {
    try {
      // API 호출: 이제 selectedRound가 아닌 매개변수로 받은 roundId를 사용합니다.
      await changeUserAttendance(roundId, userId, {
        status: newStatus,
        reason: '관리자에 의한 출석 상태 변경',
      });

      // 버전 업을 통해 AttendanceManagementCard의 useEffect가 다시 실행되어 목록을 갱신합니다.
      setRoundAttendanceVersion((prev) => prev + 1);

      // 선택 사항: 성공 토스트
      // toast.success('출석 상태가 변경되었습니다.');
    } catch (error) {
      console.error('유저 출석 상태 변경에 실패했습니다. ', error);
      alert('출석 상태 변경에 실패했습니다.');
    }
  };

  const handleRoundChange = async (roundId, updateRoundData) => {
    // setSessions((draft) => {
    //   const session = draft.find((s) => s.id === selectedSessionId);
    //   if (!session) return;
    //   const round = session.rounds.find((r) => r.id === updateRoundData.id);
    //   if (round) {
    //     round.startTime = updateRoundData.startTime;
    //     round.availableMinutes = updateRoundData.availableMinutes;
    //   }
    //   // 회차 수정 후 정렬
    //   if (session.rounds) {
    //     session.rounds.sort(
    //       (a, b) =>
    //         new Date(`${a.date}T${a.startTime}`) -
    //         new Date(`${b.date}T${b.startTime}`)
    //     );
    //   }
    // });

    try {
      await changeRoundData(roundId, updateRoundData);

      setRoundsVersion((v) => v + 1);
    } catch (error) {
      console.error('회차 수정에 실패했습니다. ', error);
    }
  };

  const handleSessionChange = async (sessionId, updateSessionData) => {
    // 낙관적 업데이트
    setSessions((draft) => {
      const session = draft.find((s) => String(s.sessionId) === String(sessionId));
      if (session && session.session) {
        if (updateSessionData.title !== undefined) session.session.title = updateSessionData.title;
        if (updateSessionData.description !== undefined) session.session.description = updateSessionData.description;
        if (updateSessionData.allowedMinutes !== undefined) session.session.allowedMinutes = updateSessionData.allowedMinutes;
        if (updateSessionData.status !== undefined) session.session.status = updateSessionData.status;
      }
    });

    try {
      await changeSessionData(sessionId, updateSessionData);

      const updatedSessions = await getAttendanceSessions();
      setSessions(updatedSessions || []);
    } catch (error) {
      console.error('세션 수정에 실패했습니다. ', error);
      // 실패 시 롤백 (전체 갱신)
      const restoredSessions = await getAttendanceSessions();
      setSessions(restoredSessions || []);
      throw error;
    }
  };

  const openRoundModifyModal = () => setRoundModifyModalOpen(true);
  const closeRoundModifyModal = () => setRoundModifyModalOpen(false);

  const openSessionModifyModal = () => setSessionModifyModalOpen(true);
  const closeSessionModifyModal = () => setSessionModifyModalOpen(false);

  const openAddUsersModal = () => setAddUsersModalOpen(true);
  const closeAddUsersModal = () => setAddUsersModalOpen(false);

  const openAddRoundsModal = () => setAddRoundsModalOpen(true);
  const closeAddRoundsModal = () => setAddRoundsModalOpen(false);

  const handleAddSession = async (sessionData) => {
    try {
      await createAttendanceSession(sessionData);
      await fetchSessions();
    } catch (error) {
      console.error('세션 생성에 실패했습니다.', error);
      throw error;
    }
  };
  const handleAddRounds = async (sessionId, newRounds) => {
    // setSessions((draft) => {
    //   const session = draft.find((session) => session.id === sessionId);
    //   if (session) {
    //     session.rounds.push(...newRounds);
    //     // 회차 추가 후 정렬
    //     session.rounds.sort(
    //       (a, b) =>
    //         new Date(`${a.date}T${a.startTime}`) -
    //         new Date(`${b.date}T${b.startTime}`)
    //     );
    //   }
    // });
    try {
      const addRoundsPromises = newRounds.map((newRound) => {
        return addRound(sessionId, newRound);
      });
      await Promise.all(addRoundsPromises);
      setRoundsVersion((v) => v + 1);
    } catch (error) {
      console.error('회차 추가에 실패했습니다. ', error);
    }
  };

  const handleDeleteSession = async (sessionId) => {
    // setSessions((draft) => {
    //   const sessionIndex = draft.findIndex((session) => {
    //     return session.id === sessionId;
    //   });
    //   if (sessionIndex !== -1) {
    //     draft.splice(sessionIndex, 1);
    //   }
    // });
    // 세션 삭제
    await deleteSession(sessionId);

    const updatedSessions = await getAttendanceSessions();
    setSessions(updatedSessions || []);

    // 세션 선택 초기화
    setSelectedSessionId(null);
    setSelectedRound(null);
  };

  const handleDeleteRound = async (roundId) => {
    // setSessions((draft) => {
    //   const session = draft.find((session) => {
    //     return session.id === selectedSessionId;
    //   });

    //   if (session) {
    //     const roundIndex = session.rounds.findIndex(
    //       (round) => round.id === roundId
    //     );
    //     if (roundIndex !== -1) {
    //       session.rounds.splice(roundIndex, 1);
    //     }
    //   }
    // });

    try {
      await deleteRound(roundId);

      setRoundsVersion((v) => v + 1);
    } catch (error) {
      console.error('회차 삭제에 실패했습니다. ', error);
    }

    // 회차 선택 초기화
    setSelectedRound(null);
  };

  const handleAddUsers = async (sessionId, userId) => {
    try {
      await addUser(sessionId, userId);
      setRoundAttendanceVersion((prev) => prev + 1);
    } catch (error) {
      console.error('유저 추가에 실패했습니다. ', error);
    }
  };

  const handleDeleteUsers = async (sessionId, userIds) => {
    try {
      const promises = userIds.map((userId) => deleteUser(sessionId, userId));
      await Promise.all(promises);
      setRoundAttendanceVersion((v) => v + 1);
    } catch (error) {
      console.error('유저 삭제 실패', error);
      alert('일부 유저 삭제에 실패했습니다.');
    }
  };

  const handleAddManager = async (sessionId, userIds) => {
    try {
      const promises = userIds.map((userId) => addManager(sessionId, userId));
      await Promise.all(promises);

      setRoundAttendanceVersion((v) => v + 1);
      alert('선택한 유저가 매니저로 격상되었습니다.');
    } catch (error) {
      console.error('매니저 추가 실패:', error);
      alert('매니저 권한 부여에 실패했습니다.');
    }
  };

  const handleRemoveManager = async (sessionId, userIds) => {
    try {
      const promises = userIds.map((userId) =>
        deleteManager(sessionId, userId)
      );
      await Promise.all(promises);

      setRoundAttendanceVersion((v) => v + 1);
      alert('선택한 유저가 일반 참가자로 변경되었습니다.');
    } catch (error) {
      console.error('매니저 제거 실패:', error);
      alert('권한 제거에 실패했습니다. (OWNER 여부 확인 필요)');
    }
  };

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

    handleDeleteSession,
    handleDeleteRound,
    isAddRoundsModalOpen,
    openAddRoundsModal,
    closeAddRoundsModal,
    roundsVersion,
    roundAttendanceVersion,

    openAddUsersModal,
    closeAddUsersModal,
    isAddUsersModalOpen,
    handleAddUsers,
    handleDeleteUsers,
    handleAddManager,
    handleRemoveManager,
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
