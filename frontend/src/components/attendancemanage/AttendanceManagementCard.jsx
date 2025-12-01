import styles from './AttendanceManagementCard.module.css';

import { useAttendance } from '../../contexts/AttendanceContext';
import { useEffect, useState } from 'react';
import { getRoundUserAttendance } from '../../utils/attendanceManage';

const attendanceEnglishToKorean = {
  PRESENT: '출석',
  LATE: '지각',
  ABSENT: '결석',
  EXCUSED: '공결',
  PENDING: '미정',
};

const AttendanceManagementCard = ({ styles: commonStyles }) => {
  const {
    selectedSessionId,
    selectedRound,
    handleAttendanceChange,
    roundAttendanceVersion,
  } = useAttendance();

  const [users, setUsers] = useState([]);

  useEffect(() => {
    const fetchUsers = async () => {
      if (selectedSessionId && selectedRound) {
        const userList = await getRoundUserAttendance(selectedRound);
        console.log(userList);
        // const sortedUsers = (userList || []).sort(
        //   (a, b) =>
        //     new Date(`${a.date}T${a.startTime}`) -
        //     new Date(`${b.date}T${b.startTime}`)
        // );
        setUsers(userList);
      } else {
        setUsers([]);
      }
    };
    fetchUsers();
  }, [selectedSessionId, selectedRound, roundAttendanceVersion]);

  return (
    <div className={styles.attendanceManagementCardContainer}>
      <header className={commonStyles.header}>
        <h1>출석 관리</h1>
      </header>
      <div className={styles.tableGroup}>
        <table className={styles.table} role="grid">
          <thead>
            <tr>
              <th>이름</th>
              <th>상태</th>
              <th>변경</th>
              <th>횟수</th>
            </tr>
          </thead>
          <tbody>
            {!selectedRound ? (
              <tr>
                <td colSpan="4" className={styles.noData}>
                  회차를 선택해주세요.
                </td>
              </tr>
            ) : users.length > 0 ? (
              users.map((user) => (
                <tr key={user.userId}>
                  <td>{user.userName}</td>
                  <td>{attendanceEnglishToKorean[user.attendanceStatus]}</td>
                  <td>
                    <select
                      className={styles.attendanceSelect}
                      value={user.attendanceStatus}
                      onChange={(e) =>
                        handleAttendanceChange(user.userId, e.target.value)
                      }
                    >
                      {/* 출석(PRESENT), 지각(LATE), 결석(ABSENT), 공결(EXCUSED)   */}
                      <option value="PRESENT">출석</option>
                      <option value="ABSENT">결석</option>
                      <option value="LATE">지각</option>
                      <option value="EXCUSED">공결</option>
                      <option value="PENDING">미정</option>
                    </select>
                  </td>
                  <td>-</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan="4" className={styles.noData}>
                  참가자가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default AttendanceManagementCard;
