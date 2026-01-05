import { useState, useEffect } from 'react';
import styles from './External.module.css';
import SearchBar from '../../components/Board/SearchBar';
import Pagination from '../../components/stockgame/Pagination';
import PortfolioItem from '../../components/external/PortfolioItem';
import Logo from '../../assets/logo.png';
import SejongLogo from '../../assets/sejong_logo.png';
import { Link } from 'react-router-dom';

const mockPortfolio = [
  {
    role: '운영진',
    time: '2',
    title: '자산 배분 전략 및 성과 보고서',
  },
  {
    role: '운영진',
    time: '3',
    title: '미래를 디자인하는 투자 여정',
  },
  {
    role: '운영진',
    time: '4',
    title: '목표 수익률 달성을 위한 핵심 자산 포트폴리오',
  },
  {
    role: '운영진',
    time: '5',
    title: '리스크 대비 성장형 포트폴리오 분석',
  },
];

const Portfolio = () => {
  const [currentPage, setCurrentPage] = useState(1);
  const [searchTerm, setSearchTerm] = useState('');
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const itemsPerPage = 4;

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

  const filteredPortfolio = mockPortfolio.filter((item) =>
    item.title.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const totalPages = Math.ceil(filteredPortfolio.length / itemsPerPage);
  const currentData = filteredPortfolio.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const handlePageChange = (page) => {
    setCurrentPage(page);
  };

  const handleSearchChange = (term) => {
    setSearchTerm(term);
    setCurrentPage(1);
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
        <span className={styles.title}>운용 포트폴리오</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.portfolio}>
        <SearchBar
          searchTerm={searchTerm}
          onSearchChange={handleSearchChange}
        />
        {currentData.map((item, index) => {
          return <PortfolioItem data={item} key={index} />;
        })}
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={handlePageChange}
        />
      </div>
    </div>
  );
};

export default Portfolio;
