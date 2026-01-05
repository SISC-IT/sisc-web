import styles from './Header.module.css';
import { useNavigate, Link } from 'react-router-dom';
import Logo from '../assets/logo.png';

const Header = ({ isRoot, onToggleSidebar, isOpen }) => {
  const nav = useNavigate();
  
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
        <Link to="/login">로그인</Link>
        <span>|</span>
        <Link to="/signup">회원가입</Link>
      </div>
    </header>
  );
};

export default Header;
