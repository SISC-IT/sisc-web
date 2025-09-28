import React from 'react';
import { useState } from 'react';
import PostItem from '../components/Board/PostItem';
import Modal from '../components/Board/Modal';
import styles from './Board.module.css';
import PlusIcon from '../assets/board_plus.svg';
import SelectArrowIcon from '../assets/boardSelectArrow.svg';
import SearchArrowIcon from '../assets/boardSearchArrow.svg';

const Board = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [posts, setPosts] = useState([]);
  const [sortOption, setSortOption] = useState('latest');

  const handleOpenModal = () => {
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setTitle('');
    setContent('');
  };

  const handleSave = () => {
    const newPost = { title, content, date: new Date() };
    setPosts([newPost, ...posts]);
    handleCloseModal();
  };

  return (
    <div className={styles.boardContainer}>
      <header className={styles.boardHeader}>
        <h1 className={styles.boardTitle}>게시판</h1>
      </header>
      <div className={styles.boardControls}>
        <div className={styles.searchContainer}>
          <input
            type="text"
            placeholder="검색어를 입력하는 중"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className={styles.searchInput}
          />
          <button
            onClick={() => console.log('검색어:', searchTerm)}
            className={styles.searchButton}
          >
            <img src={SearchArrowIcon} alt="검색" width="20" height="16" />
          </button>
        </div>
        <div className={styles.boardActions}>
          <div className={styles.selectWrapper}>
            <select
              className={styles.sortSelect}
              value={sortOption}
              onChange={(e) => setSortOption(e.target.value)}
            >
              <option value="latest">최신순</option>
              <option value="oldest">오래된순</option>
              <option value="popular">인기순</option>
            </select>
            <img
              src={SelectArrowIcon}
              alt="화살표"
              className={styles.selectArrow}
            />
          </div>
          <button onClick={handleOpenModal} className={styles.writeButton}>
            <span>글 작성하기</span>
            <img src={PlusIcon} alt="plus" />
          </button>
        </div>
      </div>
      <div className={styles.postsContainer}>
        {posts.map((post, index) => (
          <PostItem key={index} post={post} />
        ))}
      </div>

      {showModal && (
        <Modal
          title={title}
          setTitle={setTitle}
          content={content}
          setContent={setContent}
          onSave={handleSave}
          onClose={handleCloseModal}
        />
      )}
    </div>
  );
};

export default Board;
