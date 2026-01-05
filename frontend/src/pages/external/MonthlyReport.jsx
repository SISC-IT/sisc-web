import { useState, useEffect } from 'react';
import styles from './MonthlyReport.module.css';
import logo from '../../assets/logo.png';
import sejongLogo from '../../assets/sejong_logo.png';
import Report from '../../components/external/Report';
import { Link } from 'react-router-dom';

const MonthlyReport = () => {
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
    <div className={styles.page}>
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
          <img src={logo} alt="세종투자연구회 로고" />
          <span>세종투자연구회</span>
        </Link>
      </header>

      {/* 모바일 사이드바 */}
      {isMobileMenuOpen && (
        <>
          <div className={styles.overlay} onClick={closeMobileMenu}></div>
          <nav className={styles.mobileSidebar}>
            <Link to="/main" className={styles.sidebarHeader} onClick={closeMobileMenu}>
              <img src={logo} alt="세종투자연구회 로고" />
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
        <div className={styles.logoSection}>
          <img src={logo} alt="세종투자연구회" className={styles.logo} />
          <span className={styles.logoName}>월간 세투연</span>
        </div>
        <h1 className={styles.title}>
          매월 업데이트되는{' '}
          <strong className={styles.strong}>세투연 콘텐츠</strong>를 <br /> 한
          곳에 모았습니다.
        </h1>
        <h2 className={styles.subTitle}>
          지난 한 달의 활동과 자료들을 아카이브 형식으로 정리했어요.
        </h2>
      </div>
      <div className={styles.countSection}>
        <span className={styles.count}>
          <strong style={{ color: '#339FFF' }}>4개</strong>의 게시물
        </span>
      </div>
      <div className={styles.reportSection}>
        <Report />
        <Report />
        <Report />
        <Report />
      </div>
    </div>
  );
};

export default MonthlyReport;
