import { useState, useEffect } from 'react';
import styles from './External.module.css';
import Filter from '../../components/external/Filter';
import MemberCard from '../../components/external/MemberCard';
import { executivesByGeneration } from '../../utils/executiveByGeneration';
import Logo from '../../assets/logo.png';
import SejongLogo from '../../assets/sejong_logo.png';
import { Link } from 'react-router-dom';

const cohort = Array.from({ length: 24 }, (_, i) => `${24 - i}기`);

const Leaders = () => {
  const [selected, setSelected] = useState(cohort[0]);
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
        <span className={styles.title}>임원진 소개</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}>
        <div className={styles.filter}>
          <Filter items={cohort} value={selected} onChange={setSelected} />
        </div>
        <MemberCard datas={executivesByGeneration[selected]} />
      </div>
    </div>
  );
};

export default Leaders;
