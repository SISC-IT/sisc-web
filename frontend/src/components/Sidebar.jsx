import { Link } from 'react-router-dom';
import './Sidebar.css';

const Sidebar = () => {
  return (
    <div className="sidebar">
      <div className="sidebar-header">
        <div className="sidebar-header-logo"></div>
        <div className="sidebar-header-text">
          <p className="sidebar-header-text-ko">세종투자연구회</p>
          <p className="sidebar-header-text-en">Finance . IT</p>
        </div>
      </div>
      <hr style={{ width: '163px', marginBottom: '30px' }} />
      <nav aria-label="사이드바">
        <div className="menu-section">
          <h3 className="menu-title">Main</h3>
          <ul>
            <li>
              <Link to="/">홈</Link>
            </li>
            <li>
              <Link to="/board">게시판</Link>
            </li>
          </ul>
        </div>

        <div className="menu-section">
          <h3 className="menu-title">출석체크</h3>
          <ul>
            <li>
              <Link to="/attendance">출석하기</Link>
            </li>
            <li>
              <Link to="/attendance/manage">출석관리(담당자)</Link>
            </li>
          </ul>
        </div>

        <div className="menu-section">
          <h3 className="menu-title">트레이딩</h3>
          <ul>
            <li>
              <Link to="/quant-bot">퀀트봇</Link>
            </li>
            <li>
              <Link to="/stock-game">주식베팅</Link>
            </li>
            <li>
              <Link to="/back-test">백테스팅</Link>
            </li>
          </ul>
        </div>

        <div className="menu-section">
          <h3 className="menu-title">계정</h3>
          <ul>
            <li>
              <Link to="/mypage">마이페이지</Link>
            </li>
          </ul>
        </div>
      </nav>
    </div>
  );
};

export default Sidebar;
