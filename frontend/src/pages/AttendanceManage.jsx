import {
  AttendanceProvider,
  useAttendance,
} from '../contexts/AttendanceContext';
import styles from './AttendanceManage.module.css';

import SessionSettingCard from '../components/attendancemanage/SessionSettingCard';
import AttendanceManagementCard from '../components/attendancemanage/AttendanceManagementCard';
import SessionManagementCard from '../components/attendancemanage/SessionManagementCard';
import RoundModifyModal from '../components/attendancemanage/RoundModifyModal';
import SessionModifyModal from '../components/attendancemanage/SessionModifyModal';
import RoundDayPicker from '../components/attendancemanage/RoundDayPicker';

import { ToastContainer } from 'react-toastify';
import {
  getAttendanceSessions,
  addUser,
  getUsers,
  getAttendance,
  getSessionAttendance,
  getRounds,
  changeUserAttendance,
  getRoundUserAttendance,
  getUserList,
} from '../utils/attendanceManage';
import AddUsersModal from '../components/attendancemanage/AddUsersModal';

const AttendanceContent = () => {
  const {
    sessions,
    isRoundModifyModalOpen,
    editingRound,
    closeRoundModifyModal,
    handleRoundChange,
    isSessionModifyModalOpen,
    editingSession,
    closeSessionModifyModal,
    handleSessionChange,
    handleDeleteSession,
    handleDeleteRound,
    isAddRoundsModalOpen,
    selectedSessionId,
    isAddUsersModalOpen,
  } = useAttendance();

  return (
    <>
      <button
        onClick={async () => {
          console.log(await getAttendanceSessions());
        }}
      >
        세션정보확인(임시)
      </button>
      <button
        onClick={async () => {
          await addUser(
            sessions[1].attendanceSessionId,
            '97a6fd73-a59a-4e97-b043-c7a364b789b5'
          );
        }}
      >
        0번 세션에 유저 추가(임시)
      </button>
      <button
        onClick={async () => {
          console.log(await getUsers(sessions[1].attendanceSessionId));
        }}
      >
        0번 세션 참여자 조회
      </button>
      <button
        onClick={async () => {
          console.log(await getRounds(sessions[1].attendanceSessionId));
        }}
      >
        0번 세션 회차 정보 조회
      </button>
      <button
        onClick={async () => {
          console.log(
            await changeUserAttendance(
              '5a08fc34-c6c1-4b23-9d62-5f8cb2d6587f',
              'f54f1697-483b-49d8-8dc1-edb692b9c1b7',
              { status: 'PRESENT', reason: '출석 go' }
            )
          );
        }}
      >
        0번 세션, 0번 회차 유저 출석 상태 변경
      </button>
      <button
        onClick={async () => {
          const rounds = await getRounds(sessions[0].attendanceSessionId);
          console.log(
            await getRoundUserAttendance('e5e1e709-6178-4b88-aa1f-2dc63fc72f4d')
          );
        }}
      >
        0번 세션의 0번 회차에 대해 출석 상태 정보 가져오기
      </button>
      <button
        onClick={async () => {
          console.log(
            await getUserList('e5e1e709-6178-4b88-aa1f-2dc63fc72f4d')
          );
        }}
      >
        모든 유저 리스트 가져오기
      </button>

      {/* --------------------------------------------------------------- */}

      <div className={styles.cardLayout}>
        <div className={styles.leftColumn}>
          <SessionSettingCard styles={styles} />
          <SessionManagementCard styles={styles} />
        </div>
        <AttendanceManagementCard styles={styles} />
      </div>

      {isRoundModifyModalOpen && (
        <RoundModifyModal
          styles={styles}
          onClose={closeRoundModifyModal}
          sessionId={selectedSessionId}
          round={editingRound}
          onSave={handleRoundChange}
          onDelete={handleDeleteRound}
        />
      )}
      {isSessionModifyModalOpen && (
        <SessionModifyModal
          styles={styles}
          onClose={closeSessionModifyModal}
          session={editingSession}
          onSave={handleSessionChange}
          onDelete={handleDeleteSession}
        />
      )}
      {isAddRoundsModalOpen && <RoundDayPicker />}
      {isAddUsersModalOpen && <AddUsersModal />}
    </>
  );
};

// 페이지 레벨 컴포넌트
const AttendanceManage = () => {
  return (
    <AttendanceProvider>
      <ToastContainer
        position="top-center"
        autoClose={false} //
        closeOnClick={false}
        draggable={false}
        theme="light"
      />
      <div className={styles.attendanceManageContainer}>
        <div className={styles.mainTitle}>출석관리(담당자)</div>
        <AttendanceContent />
      </div>
    </AttendanceProvider>
  );
};

export default AttendanceManage;
