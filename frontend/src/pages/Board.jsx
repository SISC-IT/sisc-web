import React, { useState, useEffect } from 'react';
import PostItem from '../components/Board/PostItem';
import Modal from '../components/Board/Modal';
import SearchBar from '../components/Board/SearchBar';
import BoardActions from '../components/Board/BoardActions';
import styles from './Board.module.css';

const Board = () => {
  const [posts, setPosts] = useState(() => {
    const saved = localStorage.getItem('boardPosts');
    if (saved) {
      const parsed = JSON.parse(saved);
      return parsed.map((post) => ({
        ...post,
        date: new Date(post.date),
      }));
    }
    return [];
  });

  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [sortOption, setSortOption] = useState('latest');

  useEffect(() => {
    localStorage.setItem('boardPosts', JSON.stringify(posts));
  }, [posts]);

  const handleOpenModal = () => {
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setTitle('');
    setContent('');
  };

  const handleSave = () => {
    const newPost = {
      title,
      content,
      date: new Date(),
      id: Date.now(),
      likeCount: 0,
      isLiked: false,
      isBookmarked: false,
    };
    setPosts([newPost, ...posts]);
    handleCloseModal();
  };

  const handleLike = (postId) => {
    setPosts(
      posts.map((post) =>
        post.id === postId
          ? {
              ...post,
              isLiked: !post.isLiked,
              likeCount: post.isLiked ? post.likeCount - 1 : post.likeCount + 1,
            }
          : post
      )
    );
  };

  const handleBookmark = (postId) => {
    setPosts(
      posts.map((post) =>
        post.id === postId
          ? { ...post, isBookmarked: !post.isBookmarked }
          : post
      )
    );
  };

  const sortedPosts = [...posts].sort((a, b) => {
    if (sortOption === 'latest') {
      return new Date(b.date) - new Date(a.date);
    }
    if (sortOption === 'oldest') {
      return new Date(a.date) - new Date(b.date);
    }
    if (sortOption === 'popular') {
      return b.likeCount - a.likeCount;
    }
    return 0;
  });

  const filteredPosts = sortedPosts.filter((post) => {
    if (!searchTerm) return true;
    const lowerSearch = searchTerm.toLowerCase();
    return (
      post.title.toLowerCase().includes(lowerSearch) ||
      post.content.toLowerCase().includes(lowerSearch)
    );
  });

  return (
    <div className={styles.boardContainer}>
      <header className={styles.boardHeader}>
        <h1 className={styles.boardTitle}>게시판</h1>
      </header>

      <div className={styles.boardControls}>
        <SearchBar searchTerm={searchTerm} onSearchChange={setSearchTerm} />
        <BoardActions
          sortOption={sortOption}
          onSortChange={setSortOption}
          onWrite={handleOpenModal}
        />
      </div>

      <div className={styles.postsContainer}>
        {filteredPosts.length > 0 ? (
          filteredPosts.map((post) => (
            <PostItem
              key={post.id}
              post={post}
              onLike={handleLike}
              onBookmark={handleBookmark}
            />
          ))
        ) : (
          <p className={styles.emptyMessage}>게시글이 없습니다.</p>
        )}
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
