import styles from './External.module.css';
import SearchBar from '../../components/Board/SearchBar';
import Pagination from '../../components/stockgame/Pagination';
import PortfolioItem from '../../components/external/PortfolioItem';
import { useState } from 'react';

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
  const itemsPerPage = 4;

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
