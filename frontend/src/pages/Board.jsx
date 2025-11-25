import React, { useState, useEffect } from 'react';
import PostItem from '../components/Board/PostItem';
import Modal from '../components/Board/Modal';
import SearchBar from '../components/Board/SearchBar';
import BoardActions from '../components/Board/BoardActions';
import styles from './Board.module.css';
import { useParams } from 'react-router-dom';

const Board = () => {
  const { team } = useParams();
  console.log('=== Board 렌더링 ===');
  console.log('현재 team:', team);

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
  const [selectedFiles, setSelectedFiles] = useState([]);

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

  // 파일 선택 핸들러
  const handleFileChange = (e) => {
    const newFiles = Array.from(e.target.files);

    setSelectedFiles((prevFiles) => {
      const updatedFiles = [...prevFiles];
      const replacedFileNames = [];

      newFiles.forEach((newFile) => {
        const sameNameIndex = updatedFiles.findIndex(
          (f) => f.name === newFile.name
        );

        if (sameNameIndex !== -1) {
          // 같은 이름 발견
          if (updatedFiles[sameNameIndex].size === newFile.size) {
            // 같은 크기 = 완전 중복 (무시)
            return;
          } else {
            // 다른 크기 = 교체
            updatedFiles[sameNameIndex] = newFile;
            replacedFileNames.push(newFile.name);
            return;
          }
        }

        // 새 파일 추가
        updatedFiles.push(newFile);
      });

      // 교체된 파일이 있으면 알림
      if (replacedFileNames.length > 0) {
        alert(`교체됨: ${replacedFileNames.join(', ')}`);
      }

      return updatedFiles;
    });

    // input 초기화
    e.target.value = '';

    console.log('파일 처리 완료');
  };

  // 파일 삭제 핸들러
  const handleRemoveFile = (indexToRemove) => {
    setSelectedFiles((prevFiles) =>
      prevFiles.filter((_, index) => index !== indexToRemove)
    );
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
      files: selectedFiles.map((file) => ({
        name: file.name,
        size: file.size,
        type: file.type,
      })),
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
          filteredPosts.map((post) => {
            console.log('PostItem 렌더링, post.id:', post.id, 'team:', team);
            return (
              <PostItem
                key={post.id}
                post={post}
                currentTeam={team}
                onLike={handleLike}
                onBookmark={handleBookmark}
              />
            );
          })
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
          selectedFiles={selectedFiles}
          onFileChange={handleFileChange}
          onRemoveFile={handleRemoveFile}
          onSave={handleSave}
          onClose={handleCloseModal}
        />
      )}
    </div>
  );
};

export default Board;
