import './AttendanceSelectBox.css';

const AttendanceSelectBox = ({ icon, text, onClick }) => {
  return (
    <div>
      <div className="attendance-box" onClick={onClick}>
        <div className="at-left-content">
          <div className="at-icon-circle">
            <img src={icon} className="at_icon" />
          </div>
          <div className="at-text-contanier">
            <div className="at-text">{text}</div>
          </div>
        </div>
        <div className="at-right-arrow">˃</div>
      </div>
    </div>
  );
};

export default AttendanceSelectBox;
