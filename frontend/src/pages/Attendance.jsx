import styles from './Attendance.module.css';
import { useEffect, useState } from 'react';
import SessionSelectBox from '../components/attendance/SessionSelectBox';
// import ExcusedTime from '../components/attendance/ExcusedTime';
import SessionManage from '../components/attendance/SessionManage';
import { attendanceList } from '../utils/attendanceList';

import { useAuthGuard } from '../hooks/useAuthGuard';

const Attendance = () => {
  useAuthGuard();

  const [attendanceSessions, setAttendanceSessions] = useState([]);
  const [selectedSession, setSelectedSession] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchAttendance = async () => {
      try {
        const data = await attendanceList();
        setAttendanceSessions(Array.isArray(data) ? data : []);
      } catch {
        setError('데이터를 불러오는 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchAttendance();
  }, []);

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>출석조회</h1>
      <div className={styles.attendanceSection}>
        <SessionSelectBox
          sessions={attendanceSessions}
          selectedSession={selectedSession}
          onChange={setSelectedSession}
          disabled={loading || !!error}
        />
        {/* <ExcusedTime /> */}
      </div>
      <SessionManage
        sessions={attendanceSessions}
        selectedSession={selectedSession}
        loading={loading}
        error={error}
      />
    </div>
  );
};

export default Attendance;
