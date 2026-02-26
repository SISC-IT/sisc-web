import styles from './Attendance.module.css';
import SessionSelectBox from '../components/attendance/SessionSelectBox';
import ExcusedTime from '../components/attendance/ExcusedTime';
import SessionManage from '../components/attendance/SessionManage';

import { useAuthGuard } from '../hooks/useAuthGuard';
import { useCheckIn } from '../hooks/useCheckIn';

const Attendance = () => {
  useAuthGuard();
  useCheckIn();

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>출석하기</h1>
      <div className={styles.attendanceSection}>
        <SessionSelectBox />
        <ExcusedTime />
      </div>
      <SessionManage />
    </div>
  );
};

export default Attendance;
