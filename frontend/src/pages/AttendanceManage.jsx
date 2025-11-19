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

const AttendanceContent = () => {
  const {
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
  } = useAttendance();

  return (
    <>
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
