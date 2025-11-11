import styles from './Header.module.css';
import { useNavigate, useLocation } from 'react-router-dom';
import Logo from '../assets/logo.png';

const Header = ({ onToggleSidebar, isOpen }) => {
  const location = useLocation();
  const isRoot = location.pathname === '/';
  const nav = useNavigate();
  return (
    <header className={styles.header}>
      {isRoot && (
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
      )}
      <div className={styles.brand} onClick={() => nav('/')}>
        <img className={styles.logo} src={Logo} alt="세종투자연구회 로고" />
        <span className={styles.title}>세종투자연구회</span>
      </div>
    </header>
  );
};

export default Header;
