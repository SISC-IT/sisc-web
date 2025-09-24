import React, { useState } from 'react';
import styles from './Board.module.css';
import CreatePostModal from '../components/Board/CreatePostModal';

const PostItem = ({ title, author, time, content, authorProfile }) => (
  <div className={styles.postItem}>
    <div className={styles.postHeader}>
      <div className={styles.authorInfo}>
        <img
          src={authorProfile || '/default-avatar.png'}
          alt={`${author} 프로필`}
          className={styles.authorAvatar}
        />
        <div className={styles.authorDetails}>
          <span className={styles.authorName}>{author}</span>
          <span className={styles.postTime}>{time}</span>
        </div>
      </div>
    </div>
    <div className={styles.postContent}>
      <h3>{title}</h3>
      <p>{content}</p>
    </div>
  </div>
);

function Board() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [sortOption, setSortOption] = useState('최신순');
  const [posts, setPosts] = useState([
    {
      title: '첫 번째 공지',
      author: '운영진',
      time: '2분전',
      content:
        '안녕하세요, 투자 연구 동아리 여러분!\n다가오는 2학기 첫 정기 모임 일정을 안내드립니다....',
      views: 125,
      likes: 15,
      bookmarks: 8,
      authorProfile: null,
    },
    {
      title: '이번 주 스터디 주제 제안합니다!',
      author: '운영진',
      time: '2분전',
      content:
        '안녕하세요, 투자 연구 동아리 여러분!\n다가오는 2학기 첫 정기 모임 일정을 안내드립니다....',
      views: 89,
      likes: 23,
      bookmarks: 12,
      authorProfile: null,
    },
    {
      title: '이번 주 스터디 주제 제안합니다!',
      author: '운영진',
      time: '2분전',
      content: '안녕하세요, 투자 연구 동아리 여러분!',
      views: 245,
      likes: 7,
      bookmarks: 3,
      authorProfile: null,
    },
  ]);

  const handleNewPost = () => {
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
  };

  const handleSubmitPost = (postData) => {
    const newPost = {
      ...postData,
      author: '현재사용자',
      time: '방금 전',
      views: 0,
      likes: 0,
      bookmarks: 0,
      authorProfile: null,
    };
    setPosts([newPost, ...posts]);
    setIsModalOpen(false);
  };

  const getSortedPosts = () => {
    const sortedPosts = [...posts];

    switch (sortOption) {
      case '최신순':
        return sortedPosts;
      case '조회순':
        return sortedPosts.sort((a, b) => (b.views || 0) - (a.views || 0));
      case '좋아요순':
        return sortedPosts.sort((a, b) => (b.likes || 0) - (a.likes || 0));
      case '북마크순':
        return sortedPosts.sort(
          (a, b) => (b.bookmarks || 0) - (a.bookmarks || 0)
        );
      default:
        return sortedPosts;
    }
  };

  const handleSortChange = (e) => {
    setSortOption(e.target.value);
  };

  return (
    <main className={styles.mainContent}>
      <div className={styles.header}>
        <h1>게시판</h1>
      </div>

      <div className={styles.searchContainer}>
        <div className={styles.searchBar}>
          <span className={styles.searchPlaceholder}>검색어를 입력하는 중</span>
          <div className={styles.searchIcon}>→</div>
        </div>
      </div>

      <div className={styles.boardActions}>
        <div className={styles.actionButtons}>
          <select
            value={sortOption}
            onChange={handleSortChange}
            className={styles.sortSelect}
          >
            <option value="최신순">최신순</option>
            <option value="조회순">조회순</option>
            <option value="좋아요순">좋아요순</option>
            <option value="북마크순">북마크순</option>
          </select>
          <button className={styles.newPostButton} onClick={handleNewPost}>
            글 작성하기 <span className={styles.plusIcon}>+</span>
          </button>
        </div>
      </div>

      <div className={styles.postList}>
        {getSortedPosts().map((post, index) => (
          <PostItem key={index} {...post} />
        ))}
      </div>

      <CreatePostModal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        onSubmit={handleSubmitPost}
      />
    </main>
  );
}

export default Board;
