import { useNavigate } from 'react-router-dom';
import './Attendance.css';
import icon1 from '../assets/at_icon_1.png';
import icon2 from '../assets/at_icon_2.png';
import icon3 from '../assets/at_icon_3.png';
import AttendanceSelectBox from '../components/AttendanceSelectBox.jsx';

const attendanceSelectData = [
  { icon: icon1, text: '전체 출석', path: '/attendance-all' },
  { icon: icon2, text: '자산 운용 출석', path: '/attendance-asset' },
  { icon: icon3, text: '금융 IT 출석', path: '/attendance-financeit' },
];

const Attendance = () => {
  const nav = useNavigate();

  return (
    <>
      <div className="attendance-contanier">
        <div className="attendance-text">출석하기</div>
        <div className="attendance-list">
          {attendanceSelectData.map((item, idx) => {
            return (
              <AttendanceSelectBox
                key={idx}
                icon={item.icon}
                text={item.text}
                onClick={() => nav(item.path)}
              />
            );
          })}
        </div>
      </div>
    </>
  );
};

export default Attendance;
