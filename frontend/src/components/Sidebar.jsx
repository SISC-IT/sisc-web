import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import styles from './Sidebar.module.css';
import { useState } from 'react';
import { api } from '../utils/axios';
import { toast } from 'react-toastify';

const Sidebar = ({ isOpen, isRoot, onClose }) => {
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
  const [isLoggedIn, setIsLoggedIn] = useState(
    !!localStorage.getItem('accessToken')
  );

  const logout = async () => {
    const accessToken = localStorage.getItem('accessToken');
    try {
      await api.post(
        '/api/auth/logout',
        {},
        {
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        }
      );
    } catch {
      toast.error('오류가 발생했습니다.');
    } finally {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      setIsLoggedIn(false);
      navigate('/');
      toast.success('로그아웃 되었습니다.');
    }
  };

  const handleNavLinkClick = () => {
    // 모바일에서 메뉴 클릭 시 사이드바 닫기
    if (window.innerWidth < 1024 && onClose) {
      onClose();
    }
  };

  return (
    <>
      {/* 모바일 오버레이 */}
      {isOpen && (
        <div 
          className={styles.overlay} 
          onClick={onClose}
          aria-hidden="true"
        />
      )}
      
      {/* 사이드바 */}
      <div
        className={`${styles.homeSidebarMenu} ${
          isOpen ? styles.open : styles.closed
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
                  handleNavLinkClick();
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
                  onClick={handleNavLinkClick}
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
                  onClick={handleNavLinkClick}
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
                  onClick={handleNavLinkClick}
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
                  onClick={handleNavLinkClick}
                >
                  주식베팅
                </NavLink>
              </li>
              <li>
                <NavLink
                  to="/backtest"
                  className={({ isActive }) =>
                    isActive ? styles['active-link'] : styles['inactive-link']
                  }
                  onClick={handleNavLinkClick}
                >
                  백테스팅
                </NavLink>
              </li>
            </ul>
          </div>

          <div className={styles['menu-section']}>
            <span className={styles['menu-title']}>계정</span>
            <ul>
              {isLoggedIn ? (
                <>
                  <li>
                    <NavLink
                      to="/mypage"
                      className={({ isActive }) =>
                        isActive
                          ? styles['active-link']
                          : styles['inactive-link']
                      }
                      onClick={handleNavLinkClick}
                    >
                      마이페이지
                    </NavLink>
                  </li>

                  <li>
                    <NavLink
                      to="/"
                      className={styles['inactive-link']}
                      onClick={(e) => {
                        logout();
                        handleNavLinkClick();
                      }}
                    >
                      로그아웃
                    </NavLink>
                  </li>
                </>
              ) : (
                <>
                  <li>
                    <NavLink
                      to="/mypage"
                      className={({ isActive }) =>
                        isActive
                          ? styles['active-link']
                          : styles['inactive-link']
                      }
                      onClick={handleNavLinkClick}
                    >
                      마이페이지
                    </NavLink>
                  </li>

                  <li>
                    <NavLink
                      to="/login"
                      className={({ isActive }) =>
                        isActive
                          ? styles['active-link']
                          : styles['inactive-link']
                      }
                      onClick={handleNavLinkClick}
                    >
                      로그인
                    </NavLink>
                  </li>

                  <li>
                    <NavLink
                      to="/signup"
                      className={({ isActive }) =>
                        isActive
                          ? styles['active-link']
                          : styles['inactive-link']
                      }
                      onClick={handleNavLinkClick}
                    >
                      회원가입
                    </NavLink>
                  </li>
                </>
              )}
            </ul>
          </div>
        </nav>
      </div>
    </>
  );
};

export default Sidebar;
