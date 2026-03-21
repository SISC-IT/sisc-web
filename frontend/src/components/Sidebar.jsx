/* eslint-disable no-unused-vars */
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import styles from './Sidebar.module.css';
import { useState, useEffect } from 'react';
import { api } from '../utils/axios';
import { toast } from 'react-toastify';
import { useAuth } from '../contexts/AuthContext';
import { getParentBoards } from '../utils/boardApi';
import { isAllBoardName, normalizeBoardPath, toBoardPath } from '../utils/boardRoute';
import DropdownArrowIcon from '../assets/boardSelectArrow.svg';

const ADMIN_VISIBLE_ROLES = ['SYSTEM_ADMIN', 'PRESIDENT', 'VICE_PRESIDENT'];
const ATTENDANCE_MANAGE_VISIBLE_ROLES = [
  'SYSTEM_ADMIN',
  'PRESIDENT',
  'VICE_PRESIDENT',
  'TEAM_LEADER',
];

const Sidebar = ({ isOpen, isRoot, onClose }) => {
  const nav = useNavigate();
  const location = useLocation();
  const [boardList, setBoardList] = useState([]);
  const [selectedBoard, setSelectedBoard] = useState('');
  const [isBoardMenuOpen, setIsBoardMenuOpen] = useState(false);
  const { isLoggedIn, logout } = useAuth();
  const [canSeeAdminMenu, setCanSeeAdminMenu] = useState(false);
  const [canSeeAttendanceManageMenu, setCanSeeAttendanceManageMenu] = useState(false);

  useEffect(() => {
    const loadParentBoards = async () => {
      try {
        const boards = await getParentBoards();
        const mappedBoards = (Array.isArray(boards) ? boards : []).map((board) => ({
          name: isAllBoardName(board.boardName)
            ? '전체 게시판'
            : String(board.boardName || '').includes('게시판')
              ? board.boardName
              : `${board.boardName} 게시판`,
          path: toBoardPath(board.boardName),
        }));

        const uniqueBoards = mappedBoards.filter(
          (item, index, array) =>
            item.path && array.findIndex((candidate) => candidate.path === item.path) === index
        );

        uniqueBoards.sort((a, b) => {
          if (a.path === '/board') return -1;
          if (b.path === '/board') return 1;
          return 0;
        });

        setBoardList(uniqueBoards);
      } catch {
        setBoardList([{ name: '전체 게시판', path: '/board' }]);
      }
    };

    loadParentBoards();
  }, []);

  useEffect(() => {
    if (!boardList.length) return;
    const currentPath = normalizeBoardPath(location.pathname);
    const currentBoard = boardList.find((item) => normalizeBoardPath(item.path) === currentPath);
    setSelectedBoard(currentBoard?.name || '');
  }, [boardList, location.pathname]);

  useEffect(() => {
    const checkAdminRole = async () => {
      if (!isLoggedIn) {
        setCanSeeAdminMenu(false);
        setCanSeeAttendanceManageMenu(false);
        return;
      }

      try {
        const { data } = await api.get('/api/user/details');
        const normalizedRole = String(data?.role || '').trim().toUpperCase();
        setCanSeeAdminMenu(ADMIN_VISIBLE_ROLES.includes(normalizedRole));
        setCanSeeAttendanceManageMenu(
          ATTENDANCE_MANAGE_VISIBLE_ROLES.includes(normalizedRole)
        );
      } catch {
        setCanSeeAdminMenu(false);
        setCanSeeAttendanceManageMenu(false);
      }
    };

    checkAdminRole();
  }, [isLoggedIn]);

  const handleLogout = async () => {
    await logout();
    toast.success('로그아웃 되었습니다.');
    nav('/login');
  };

  const handleNavLinkClick = () => {
    // 모바일에서 메뉴 클릭 시 사이드바 닫기
    if (window.innerWidth < 1024 && onClose) {
      onClose();
    }
  };

  const handleBoardSelect = (board) => {
    if (!board?.path) return;

    setSelectedBoard(board.name);
    setIsBoardMenuOpen(false);

    const currentPath = normalizeBoardPath(location.pathname);
    const targetPath = normalizeBoardPath(board.path);
    const nextPath = normalizeBoardPath(board.path);

    if (currentPath === targetPath) {
      nav(nextPath, {
        replace: false,
        state: { boardSwitchAt: Date.now() },
      });
    } else {
      nav(nextPath);
    }

    handleNavLinkClick();
  };

  return (
    <>
      {/* 모바일 오버레이 */}
      {isOpen && (
        <div className={styles.overlay} onClick={onClose} aria-hidden="true" />
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
            <span className={styles['menu-title']}>Main</span>

            <button
              type="button"
              className={styles.menuTitleToggle}
              onClick={() => setIsBoardMenuOpen((prev) => !prev)}
              aria-expanded={isBoardMenuOpen}
              aria-controls="sidebar-board-list"
            >
              게시판
              <span
                className={`${styles.menuTitleToggleIcon} ${
                  isBoardMenuOpen ? styles.menuTitleToggleIconOpen : ''
                }`}
              >
                <img src={DropdownArrowIcon} alt="토글" />
              </span>
            </button>

            {isBoardMenuOpen && (
              <ul id="sidebar-board-list" className={styles.boardMenuList}>
                {boardList.length > 0 ? (
                  boardList.map((item) => (
                    <li key={item.path}>
                      <button
                        type="button"
                        className={`${styles.boardMenuItem} ${
                          selectedBoard === item.name
                            ? styles.boardMenuItemActive
                            : ''
                        }`}
                        onClick={() => handleBoardSelect(item)}
                      >
                        {item.name}
                      </button>
                    </li>
                  ))
                ) : (
                  <li className={styles.boardMenuLoading}>게시판 로딩 중...</li>
                )}
              </ul>
            )}
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
                  출석조회
                </NavLink>
              </li>
              {isLoggedIn && canSeeAttendanceManageMenu && (
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
              )}
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
              <li>
                <NavLink
                  to="/mypage"
                  className={({ isActive }) =>
                    isActive ? styles['active-link'] : styles['inactive-link']
                  }
                  onClick={handleNavLinkClick}
                >
                  마이페이지
                </NavLink>
              </li>

              {isLoggedIn && canSeeAdminMenu && (
                <li>
                  <NavLink
                    to="/admin"
                    className={({ isActive }) =>
                      isActive ? styles['active-link'] : styles['inactive-link']
                    }
                    onClick={handleNavLinkClick}
                  >
                    관리자
                  </NavLink>
                </li>
              )}

              {isLoggedIn ? (
                <li>
                  <NavLink
                    to="/"
                    className={styles['inactive-link']}
                    onClick={(e) => {
                      e.preventDefault();
                      handleLogout();
                      handleNavLinkClick();
                    }}
                  >
                    로그아웃
                  </NavLink>
                </li>
              ) : (
                <>
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
