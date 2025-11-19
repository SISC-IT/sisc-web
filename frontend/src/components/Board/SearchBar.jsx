import React from 'react';
import styles from './SearchBar.module.css';
import SearchArrowIcon from '../../assets/boardSearchArrow.svg';

const SearchBar = ({ searchTerm, onSearchChange }) => {
  const handleSearch = () => {
    if (searchTerm.trim()) {
      console.log('검색어:', searchTerm);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  return (
    <div className={styles.searchContainer}>
      <input
        type="text"
        placeholder="검색어를 입력하세요"
        value={searchTerm}
        onChange={(e) => onSearchChange(e.target.value)}
        onKeyPress={handleKeyPress}
        className={styles.searchInput}
        aria-label="검색"
      />
      <button
        onClick={handleSearch}
        className={styles.searchButton}
        aria-label="검색 버튼"
      >
        <img src={SearchArrowIcon} alt="" />
      </button>
    </div>
  );
};

export default SearchBar;
