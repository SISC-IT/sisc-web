import { useEffect, useState } from 'react';
import styles from './SessionManage.module.css';
import { ClipboardCheck } from 'lucide-react';
import { attendanceList } from '../../utils/attendanceList';

const SessionManage = () => {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchAttendance = async () => {
      try {
        const data = await attendanceList();
        const normalizedSessions = Array.isArray(data)
          ? data.filter((item) => item && typeof item === 'object')
          : [];
        setSessions(normalizedSessions);
      } catch (err) {
        setError('데이터를 불러오는 중 오류가 발생했습니다.');
        setSessions([]);
      } finally {
        setLoading(false);
      }
    };

    fetchAttendance();
  }, []);

  if (loading) return <div>로딩 중...</div>;
  if (error) return <div>{error}</div>;

  return (
    <div className={styles.card}>
      <div className={styles.title}>
        <ClipboardCheck />
        세션 관리
      </div>

      <table className={styles.table} role="grid">
        <thead>
          <tr>
            <th>일자</th>
            <th>출석시작시간</th>
            <th>출석가능시간</th>
            <th>회차</th>
            <th>이름</th>
            <th></th>
          </tr>
        </thead>

        <tbody>
          {(Array.isArray(sessions) ? sessions : []).map((s) => (
            <tr key={s.attendanceId}>
              <td>{new Date(s.createdAt).toLocaleDateString()}</td>
              <td>{new Date(s.checkedAt).toLocaleTimeString()}</td>
              <td>30분</td> 
              <td>{s.roundId}</td>
              <td>{s.userName}</td>
              <td>
                <button className={styles.button}>출석</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default SessionManage;