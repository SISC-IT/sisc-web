import { NavLink } from 'react-router-dom';
import styles from './Sidebar.module.css';

const Sidebar = () => {
  return (
    <div className={styles['sidebar']}>
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
      <nav aria-label="사이드바">
        <div className={styles['menu-section']}>
          <h3 className={styles['menu-title']}>Main</h3>
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
          <h3 className={styles['menu-title']}>출석체크</h3>
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
          <h3 className={styles['menu-title']}>트레이딩</h3>
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
          <h3 className={styles['menu-title']}>계정</h3>
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
    </div>
  );
};

export default Sidebar;
