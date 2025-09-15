import { useNavigate } from 'react-router-dom';
import './Attendance.css';
import icon1 from '../assets/at_icon_1.png';
import icon2 from '../assets/at_icon_2.png';
import icon3 from '../assets/at_icon_3.png';

const Attendance = () => {
  const nav = useNavigate();

  return (
    <>
      <div className="attendance-contanier">
        <div className="attendance-text">출석하기</div>
        <div className="attendance-list">
          <div
            className="attendance-box"
            onClick={() => nav('/attendance-all')}
          >
            <div className="at-left-content">
              <div className="at-icon-circle">
                <img src={icon1} className="at_icon" />
              </div>
              <div className="at-text-contanier">
                <div className="at-text">전체 출석</div>
              </div>
            </div>
            <div className="at-right-arrow">˃</div>
          </div>
          <div
            className="attendance-box"
            onClick={() => nav('/attendance-asset')}
          >
            <div className="at-left-content">
              <div className="at-icon-circle">
                <img src={icon2} className="at_icon" />
              </div>
              <div className="at-text-contanier">
                <div className="at-text">자산 운용 출석</div>
              </div>
            </div>
            <div className="at-right-arrow">˃</div>
          </div>
          <div
            className="attendance-box"
            onClick={() => nav('/attendance-financeit')}
          >
            <div className="at-left-content">
              <div className="at-icon-circle">
                <img src={icon3} className="at_icon" />
              </div>
              <div className="at-text-contanier">
                <div className="at-text">금융 IT 출석</div>
              </div>
            </div>
            <div className="at-right-arrow">˃</div>
          </div>
        </div>
      </div>
    </>
  );
};

export default Attendance;
