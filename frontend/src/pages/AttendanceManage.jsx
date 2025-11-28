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
  deleteSession,
  addRound,
  deleteRound,
  getRounds,
  addUser,
  getUsers,
} from '../utils/attendanceManage';

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
            sessions[0].attendanceSessionId,
            'f54f1697-483b-49d8-8dc1-edb692b9c1b7'
          );
        }}
      >
        0번 세션에 유저 추가(임시)
      </button>
      <button
        onClick={async () => {
          console.log(await getUsers(sessions[0].attendanceSessionId));
        }}
      >
        0번 세션 참여자 조회
      </button>

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
