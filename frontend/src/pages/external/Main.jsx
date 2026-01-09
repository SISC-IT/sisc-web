import { useState, useEffect } from 'react';
import styles from './Main.module.css';
import image from '../../assets/external/external-image.png';
import Logo from '../../assets/logo.png';
import SejongLogo from '../../assets/sejong_logo.png';
import { Link } from 'react-router-dom';

const Main = () => {
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
      <img src={image} alt="메인 사진" className={styles.image} />

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

      {/* 태블릿/데스크톱 메뉴 (기존 유지) */}
      <nav className={styles.menu}>
        <ul>
          <li>
            <Link to="/main/intro">동아리 소개</Link>
          </li>
          <li>
            <Link to="/main/leaders">임원소개</Link>
          </li>
          <li>
            <Link to="/main/portfolio">운용 포트폴리오</Link>
          </li>
          <li>
            <Link to="/main/monthly-report">월간 세투연</Link>
          </li>
          <li>
            <Link to="/">웹사이트</Link>
          </li>
        </ul>
      </nav>

      {/* 메인 콘텐츠 */}
      <div className={styles.info}>
        <img src={Logo} alt="로고" className={styles.logo} />
        <h1 className={styles.title}>Sejong Investment Scholars Club</h1>
        <h2 className={styles.subTitle}>
          세투연과 세상을 읽고 미래에 투자하라
        </h2>
      </div>
    </div>
  );
};

export default Main;
