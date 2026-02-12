import styles from './Header.module.css';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import Logo from '../assets/logo.png';
import { useState, useEffect } from 'react';
import { api } from '../utils/axios';
import { toast } from 'react-toastify';
import { useAuth } from '../contexts/AuthContext';

const Header = ({ isRoot, onToggleSidebar, isOpen }) => {
  const nav = useNavigate();
  const location = useLocation();
  const { isLoggedIn, logout } = useAuth();

  const handleLogout = async () => {
    await logout();
    toast.success('로그아웃 되었습니다.');
    navigate('/');
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
          <button onClick={handleLogout} className={styles.logoutButton}>
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
