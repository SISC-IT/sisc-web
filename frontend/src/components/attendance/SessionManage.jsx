import styles from './SessionManage.module.css';
import { ClipboardCheck } from 'lucide-react';
import { attendanceList } from '../../utils/attendanceList';

const SessionManage = () => {
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
          {attendanceList.map((s) => (
            <tr key={s.date}>
              <td>{s.date}</td>
              <td>{s.startTime}</td>
              <td>{s.available}분</td>
              <td>{s.round}회차</td>
              <td>{s.name}</td>
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
