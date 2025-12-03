import React, { useState } from 'react';
import styles from './SearchBar.module.css';
import SearchArrowIcon from '../../assets/boardSearchArrow.svg';

const SearchBar = ({ onSearch }) => {
  const [inputValue, setInputValue] = useState('');

  const handleSearch = () => {
    if (inputValue.trim()) {
      onSearch(inputValue);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  return (
    <div className={styles.searchContainer}>
      <input
        type="text"
        placeholder="검색어를 입력하세요"
        value={inputValue}
        onChange={(e) => setInputValue(e.target.value)}
        onKeyDown={handleKeyDown}
        className={styles.searchInput}
      />
      <button onClick={handleSearch} className={styles.searchButton}>
        <img src={SearchArrowIcon} alt="검색" />
      </button>
    </div>
  );
};

export default SearchBar;
