import styles from './Header.module.css';
import { useNavigate } from 'react-router-dom';
import Logo from '../assets/logo.png';

const Header = ({ onToggleSidebar, isOpen }) => {
  const nav = useNavigate();
  return (
    <header className={styles.header}>
      <div className={styles.left}>
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
      </div>
      <div className={styles.right}>
        <button className={styles.login} onClick={() => nav('/login')}>
          로그인
        </button>
        <button className={styles.signUp} onClick={() => nav('/signup')}>
          회원가입
        </button>
      </div>
    </header>
  );
};

export default Header;
