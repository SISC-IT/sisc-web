import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import styles from './Sidebar.module.css';
import Logo from '../assets/logo.png';
import { useState } from 'react';

const Sidebar = () => {
  const location = useLocation();
  const isRoot = location.pathname === '/';
  const [isOpen, setIsOpen] = useState(false);
  const toggleSidebar = () => setIsOpen(!isOpen);
  const nav = useNavigate();

  const menuSections = (
    <>
      <nav aria-label="사이드바">
        <div className={styles['menu-section']}>
          <span className={styles['menu-title']}>Main</span>
          <ul>
            <li>
              <NavLink
                to="/"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                홈
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/board"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                게시판
              </NavLink>
            </li>
          </ul>
        </div>

        <div className={styles['menu-section']}>
          <span className={styles['menu-title']}>출석체크</span>
          <ul>
            <li>
              <NavLink
                to="/attendance"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                출석하기
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/attendance-manage"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                출석관리(담당자)
              </NavLink>
            </li>
          </ul>
        </div>

        <div className={styles['menu-section']}>
          <span className={styles['menu-title']}>트레이딩</span>
          <ul>
            <li>
              <NavLink
                to="/quant-bot"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                퀀트봇
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/stock-game"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                주식베팅
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/back-test"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                백테스팅
              </NavLink>
            </li>
          </ul>
        </div>

        <div className={styles['menu-section']}>
          <span className={styles['menu-title']}>계정</span>
          <ul>
            <li>
              <NavLink
                to="/mypage"
                className={({ isActive }) =>
                  isActive ? styles['active-link'] : styles['inactive-link']
                }
              >
                마이페이지
              </NavLink>
            </li>
          </ul>
        </div>
      </nav>
    </>
  );

  if (isRoot) {
    return (
      <div>
        {/* 홈에서는 버튼만 고정 */}
        <div className={styles.left}>
          <button
            className={styles.menuButton}
            onClick={toggleSidebar}
            aria-label={isOpen ? '메뉴 닫기' : '메뉴 열기'}
            aria-expanded={isOpen}
          >
            <span></span>
            <span></span>
            <span></span>
          </button>
          <div className={styles.brand} onClick={() => nav('/')}>
            <img className={styles.logo} src={Logo} alt="세종투자연구회 로고" />
            <span className={styles.title}>세종투자연구회</span>
          </div>
        </div>

        {/* 클릭 시 사이드바 슬라이드 */}
        <div
          className={`${styles.homeSidebarMenu} ${
            isOpen ? styles.open : styles.closed
          }`}
          aria-hidden={!isOpen}
        >
          {menuSections}
        </div>
      </div>
    );
  }

  // 홈 외 페이지는 항상 열려있음
  return (
    <div className={styles.sidebar}>
      <div className={styles['sidebar-header']}>
        <div className={styles['sidebar-header-logo']}></div>
        <div className={styles['sidebar-header-text']}>
          <span className={styles['sidebar-header-text-ko']}>
            세종투자연구회
          </span>
          <span className={styles['sidebar-header-text-en']}>Finance . IT</span>
        </div>
      </div>
      <hr
        style={{
          width: '163px',
          marginBottom: '30px',
          border: 'none',
          height: '1px',
          backgroundColor: '#656565',
        }}
      />
      {menuSections}
    </div>
  );
};

export default Sidebar;
