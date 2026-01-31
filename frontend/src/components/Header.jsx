import styles from './Header.module.css';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import Logo from '../assets/logo.png';
import { useState, useEffect } from 'react';
import { api } from '../utils/axios';
import { toast } from 'react-toastify';

const Header = ({ isRoot, onToggleSidebar, isOpen }) => {
  const nav = useNavigate();
  const location = useLocation();
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [loading, setLoading] = useState(true);

  // 로그인 상태 확인 - location 변경 시마다 재확인
  useEffect(() => {
    const checkLoginStatus = async () => {
      try {
        await api.get('/api/user/details');
        setIsLoggedIn(true);
      } catch (error) {
        setIsLoggedIn(false);
      } finally {
        setLoading(false);
      }
    };
    
    checkLoginStatus();
  }, [location.pathname]);

  const logout = async () => {
    try {
      await api.post('/api/auth/logout');
    } catch (error) {
      console.log('로그아웃 API 호출 실패:', error.message);
    } finally {
      // localStorage 유저 정보 삭제
      localStorage.removeItem('user');
      
      setIsLoggedIn(false);
      nav('/');
      toast.success('로그아웃 되었습니다.');
    }
  };
  
  return (
    <header className={`${styles.header} ${isRoot ? styles.transparent : ''}`}>
      {/* 모바일/태블릿에서는 항상 햄버거 버튼 표시 */}
      <button
        className={styles.menuButton}
        onClick={onToggleSidebar}
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
      
      <div className={styles.authLinks}>
        {isLoggedIn ? (
          <button onClick={logout} className={styles.logoutButton}>
            로그아웃
          </button>
        ) : (
          <>
            <Link to="/login">로그인</Link>
            <span>|</span>
            <Link to="/signup">회원가입</Link>
          </>
        )}
      </div>
    </header>
  );
};

export default Header;
