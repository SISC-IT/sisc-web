import { useState, useEffect } from 'react';
import styles from './External.module.css';
import Filter from '../../components/external/Filter';
import Info from '../../components/external/Info';
import Logo from '../../assets/logo.png';
import SejongLogo from '../../assets/sejong_logo.png';
import { Link } from 'react-router-dom';

const teams = [
  '증권 1팀',
  '증권 2팀',
  '증권 3팀',
  '자산 운용팀',
  '금융 IT팀',
  '매크로팀',
  '트레이딩팀',
];

const Intro = () => {
  const [selected, setSelected] = useState(teams[0]);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth >= 768) {
        setIsMobileMenuOpen(false);
      }
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const toggleMobileMenu = () => {
    setIsMobileMenuOpen(!isMobileMenuOpen);
  };

  const closeMobileMenu = () => {
    setIsMobileMenuOpen(false);
  };

  return (
    <div className={styles.container}>
      {/* 모바일 헤더 */}
      <header className={styles.mobileHeader}>
        <button
          className={styles.hamburger}
          onClick={toggleMobileMenu}
          aria-label="메뉴 열기"
        >
          <span className={styles.hamburgerLine}></span>
          <span className={styles.hamburgerLine}></span>
          <span className={styles.hamburgerLine}></span>
        </button>
        <Link to="/main" className={styles.headerLogo}>
          <img src={Logo} alt="세종투자연구회 로고" />
          <span>세종투자연구회</span>
        </Link>
      </header>

      {/* 모바일 사이드바 */}
      {isMobileMenuOpen && (
        <>
          <div className={styles.overlay} onClick={closeMobileMenu}></div>
          <nav className={styles.mobileSidebar}>
            <Link to="/main" className={styles.sidebarHeader} onClick={closeMobileMenu}>
              <img src={Logo} alt="세종투자연구회 로고" />
              <span>세종투자연구회</span>
            </Link>
            <ul className={styles.sidebarMenu}>
              <li>
                <Link to="/main/intro" onClick={closeMobileMenu}>
                  동아리 소개
                </Link>
              </li>
              <li>
                <Link to="/main/leaders" onClick={closeMobileMenu}>
                  임원소개
                </Link>
              </li>
              <li>
                <Link to="/main/portfolio" onClick={closeMobileMenu}>
                  운용 포트폴리오
                </Link>
              </li>
              <li>
                <Link to="/" onClick={closeMobileMenu}>
                  웹사이트
                </Link>
              </li>
            </ul>
          </nav>
        </>
      )}

      <div className={styles.header}>
        <span className={styles.title}>동아리 소개</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}>
        <div className={styles.filter}>
          <div className={styles.logoSection}>
            <img src={SejongLogo} alt="세종투자연구회" className={styles.logo} />
            <span className={styles.name}>Sejong Investment Scholars Club</span>
          </div>
          <Filter items={teams} value={selected} onChange={setSelected} />
        </div>
        <div className={styles.content}>
          <Info team={selected} />
        </div>
      </div>
    </div>
  );
};

export default Intro;
