import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import styles from './Sidebar.module.css';
import { useState } from 'react';

const Sidebar = ({ isOpen, isRoot }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const boardList = [
    { name: '전체 게시판', path: '/board' },
    { name: '증권1팀 게시판', path: '/board/securities-1' },
    { name: '증권2팀 게시판', path: '/board/securities-2' },
    { name: '증권3팀 게시판', path: '/board/securities-3' },
    { name: '자산운용팀 게시판', path: '/board/asset-management' },
    { name: '금융IT팀 게시판', path: '/board/finance-it' },
    { name: '매크로팀 게시판', path: '/board/macro' },
    { name: '트레이딩팀 게시판', path: '/board/trading' },
  ];

  const currentBoard = boardList.find(
    (item) => item.path === location.pathname
  );
  const [selectedBoard, setSelectedBoard] = useState(
    currentBoard?.name || '전체 게시판'
  );

  return (
    <div>
      {/* 클릭 시 사이드바 슬라이드 */}
      <div
        className={`${styles.homeSidebarMenu} ${
          !isOpen && isRoot ? styles.closed : styles.open
        }`}
        aria-hidden={!isOpen}
      >
        <nav aria-label="사이드바">
          <div className={styles['menu-section']}>
            <span className={styles['menu-title']}>게시판</span>

            <select
              className={styles.boardSelect}
              value={selectedBoard}
              onChange={(e) => {
                const newBoard = e.target.value;
                const selected = boardList.find(
                  (item) => item.name === newBoard
                );
                if (selected) {
                  setSelectedBoard(newBoard);
                  navigate(selected.path);
                }
              }}
            >
              {boardList.map((item) => (
                <option key={item.name} value={item.name}>
                  {item.name}
                </option>
              ))}
            </select>
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
              <li>
                <NavLink
                  to="/login"
                  className={({ isActive }) =>
                    isActive ? styles['active-link'] : styles['inactive-link']
                  }
                >
                  로그인
                </NavLink>
              </li>
              <li>
                <NavLink
                  to="/signup"
                  className={({ isActive }) =>
                    isActive ? styles['active-link'] : styles['inactive-link']
                  }
                >
                  회원가입
                </NavLink>
              </li>
            </ul>
          </div>
        </nav>
      </div>
    </div>
  );
};

export default Sidebar;
