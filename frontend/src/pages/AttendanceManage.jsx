import {
  AttendanceProvider,
  useAttendance,
} from '../contexts/AttendanceContext';
import styles from './AttendanceManage.module.css';

import SessionSettingCard from '../components/attendancemanage/SessionSettingCard';
import AttendanceManagementCard from '../components/attendancemanage/AttendanceManagementCard';
import SessionManagementCard from '../components/attendancemanage/SessionManagementCard';
import RoundModifyModal from '../components/attendancemanage/RoundModifyModal';
import RoundDayPicker from '../components/attendancemanage/RoundDayPicker';

import AddUsersModal from '../components/attendancemanage/AddUsersModal';

const AttendanceContent = () => {
  const {
    sessions,
    isRoundModifyModalOpen,
    editingRound,
    closeRoundModifyModal,
    handleRoundChange,
    handleDeleteRound,
    isAddRoundsModalOpen,
    selectedSessionId,
    isAddUsersModalOpen,
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
          sessionId={selectedSessionId}
          round={editingRound}
          onSave={handleRoundChange}
          onDelete={handleDeleteRound}
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
      <div className={styles.attendanceManageContainer}>
        <div className={styles.mainTitle}>출석관리(담당자)</div>
        <AttendanceContent />
      </div>
    </AttendanceProvider>
  );
};

export default AttendanceManage;
