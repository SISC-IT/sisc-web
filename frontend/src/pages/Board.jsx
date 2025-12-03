import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams } from 'react-router-dom';
import PostItem from '../components/Board/PostItem';
import Modal from '../components/Board/Modal';
import SearchBar from '../components/Board/SearchBar';
import BoardActions from '../components/Board/BoardActions';
import CategoryTabs from '../components/Board/CategoryTabs';
import CreateSubBoardModal from '../components/Board/CreateSubBoardModal';
import styles from './Board.module.css';
import * as boardApi from '../utils/boardApi';

const Board = () => {
  const { team } = useParams();

  const [boardIdMap, setBoardIdMap] = useState({});
  const [boardNameMap, setBoardNameMap] = useState({});
  const [posts, setPosts] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [sortOption, setSortOption] = useState('latest');
  const [loading, setLoading] = useState(false);
  const [boardsLoaded, setBoardsLoaded] = useState(false);
  const [activeSubBoard, setActiveSubBoard] = useState('all');
  const [showSubBoardModal, setShowSubBoardModal] = useState(false);
  const [subBoardName, setSubBoardName] = useState('');
  const [subBoardTabs, setSubBoardTabs] = useState([]);

  const prevPostsRef = useRef(posts);

  const currentPath = team ? `/board/${team}` : '/board';
  const currentBoardId = boardIdMap[currentPath];
  const currentBoardName = boardNameMap[currentPath];

  useEffect(() => {
    prevPostsRef.current = posts;
  }, [posts]);

  useEffect(() => {
    loadBoardList();
  }, []);

  const loadBoardList = async () => {
    try {
      const boards = await boardApi.getParentBoards();

      if (!boards || boards.length === 0) {
        console.warn('게시판이 없습니다.');
        alert('등록된 게시판이 없습니다.');
        setBoardsLoaded(true);
        return;
      }

      const idMap = {};
      const nameMap = {};

      const nameToPath = {
        증권1팀: 'securities-1',
        증권2팀: 'securities-2',
        증권3팀: 'securities-3',
        자산운용: 'asset-management',
        금융IT: 'finance-it',
        매크로: 'macro',
        트레이딩: 'trading',
      };

      boards.forEach((board) => {
        const boardName = board.boardName;
        const path =
          boardName === '전체' ? '/board' : `/board/${nameToPath[boardName]}`;

        if (path) {
          idMap[path] = board.boardId;
          nameMap[path] = boardName;
        }
      });

      setBoardIdMap(idMap);
      setBoardNameMap(nameMap);
      setBoardsLoaded(true);
    } catch (error) {
      console.error('게시판 목록 불러오기 실패:', error);
      setBoardsLoaded(true);
    }
  };

  useEffect(() => {
    const loadSubBoards = async () => {
      if (currentBoardId && !Array.isArray(currentBoardId)) {
        try {
          const subBoards = await boardApi.getSubBoards(currentBoardId);

          if (subBoards && subBoards.length > 0) {
            const tabs = subBoards.map((board) => ({
              id: board.boardId,
              name: board.boardName,
            }));
            setSubBoardTabs(tabs);
            setActiveSubBoard(tabs[0].id);

            const response = await boardApi.getPosts(tabs[0].id);
            const postsData = response.content || [];
            setPosts(Array.isArray(postsData) ? postsData : []);
          } else {
            setSubBoardTabs([]);
            setActiveSubBoard(null);
          }
        } catch (error) {
          console.error('하위 게시판 조회 실패:', error);
          setSubBoardTabs([]);
          setActiveSubBoard(null);
        }
      } else {
        setSubBoardTabs([]);
        setActiveSubBoard(null);
      }
    };

    if (boardsLoaded && currentBoardId) {
      loadSubBoards();
    }
  }, [boardsLoaded, currentBoardId]);

  const fetchPosts = useCallback(async () => {
    if (!currentBoardId) return;

    try {
      setLoading(true);

      if (Array.isArray(currentBoardId)) {
        const allPostsPromises = currentBoardId.map((id) =>
          boardApi.getPosts(id).catch((err) => {
            console.error(`게시판 ${id} 조회 실패:`, err);
            return { content: [] };
          })
        );

        const allPostsArrays = await Promise.all(allPostsPromises);
        const allPosts = allPostsArrays.flatMap(
          (response) => response.content || []
        );

        setPosts(allPosts);
      } else {
        const response = await boardApi.getPosts(currentBoardId);
        const postsData = response.content || [];
        setPosts(Array.isArray(postsData) ? postsData : []);
      }
    } catch (error) {
      console.error('게시글 불러오기 실패:', error);
      setPosts([]);
    } finally {
      setLoading(false);
    }
  }, [currentBoardId]);

  const handleSearch = useCallback(async () => {
    if (!currentBoardId) return;

    try {
      setLoading(true);

      if (Array.isArray(currentBoardId)) {
        const allPostsPromises = currentBoardId.map((id) =>
          boardApi.searchPosts(id, searchTerm).catch(() => ({ content: [] }))
        );

        const allPostsArrays = await Promise.all(allPostsPromises);
        const allPosts = allPostsArrays.flatMap(
          (response) => response.content || []
        );

        setPosts(allPosts);
      } else {
        const response = await boardApi.searchPosts(currentBoardId, searchTerm);
        const postsData = response.content || [];
        setPosts(postsData);
      }
    } catch (error) {
      console.error('검색 실패:', error);
      alert('검색에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  }, [currentBoardId, searchTerm]);

  useEffect(() => {
    if (boardsLoaded && currentBoardId) {
      if (searchTerm) {
        handleSearch();
      } else {
        fetchPosts();
      }
    }
  }, [searchTerm, boardsLoaded, currentBoardId, handleSearch, fetchPosts]);

  const handleTabChange = useCallback(
    async (tabId) => {
      setActiveSubBoard(tabId);

      if (!currentBoardId) return;

      try {
        setLoading(true);

        const response = await boardApi.getPosts(tabId);
        const postsData = response.content || [];
        setPosts(Array.isArray(postsData) ? postsData : []);
      } catch (error) {
        console.error('탭 변경 중 오류:', error);
        setPosts([]);
      } finally {
        setLoading(false);
      }
    },
    [currentBoardId]
  );

  const handleOpenModal = () => {
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setTitle('');
    setContent('');
    setSelectedFiles([]);
  };

  const handleOpenSubBoardModal = () => {
    setShowSubBoardModal(true);
  };

  const handleCloseSubBoardModal = () => {
    setShowSubBoardModal(false);
    setSubBoardName('');
  };

  const handleCreateSubBoard = async () => {
    if (!subBoardName.trim()) {
      alert('하위 게시판 이름을 입력해주세요.');
      return;
    }

    try {
      await boardApi.createBoard(subBoardName, currentBoardId);
      alert('하위 게시판이 생성되었습니다!');
      handleCloseSubBoardModal();

      const subBoards = await boardApi.getSubBoards(currentBoardId);
      const tabs = subBoards.map((board) => ({
        id: board.boardId,
        name: board.boardName,
      }));
      setSubBoardTabs(tabs);

      if (tabs.length > 0) {
        setActiveSubBoard(tabs[0].id);
      }
    } catch (error) {
      console.error('하위 게시판 생성 실패:', error);
      alert('하위 게시판 생성에 실패했습니다.');
    }
  };

  const handleFileChange = (e) => {
    const files = Array.from(e.target.files);
    setSelectedFiles((prevFiles) => [...prevFiles, ...files]);
  };

  const handleRemoveFile = (index) => {
    setSelectedFiles((prevFiles) => prevFiles.filter((_, i) => i !== index));
  };

  const handleSave = async () => {
    if (!currentBoardId) {
      alert('게시판 정보를 찾을 수 없습니다.');
      return;
    }

    if (subBoardTabs.length === 0) {
      alert('하위 게시판이 없습니다.\n먼저 하위 게시판을 생성해주세요.');
      return;
    }

    if (!activeSubBoard) {
      alert('하위 게시판을 선택해주세요.');
      return;
    }

    if (!title || !title.trim()) {
      alert('제목을 입력해주세요.');
      return;
    }

    if (!content || !content.trim()) {
      alert('내용을 입력해주세요.');
      return;
    }

    try {
      const postData = {
        title: title.trim(),
        content: content.trim(),
        files: selectedFiles,
      };

      await boardApi.createPost(activeSubBoard, postData);

      handleCloseModal();
      setSelectedFiles([]);

      handleTabChange(activeSubBoard);

      alert('게시글이 작성되었습니다!');
    } catch (error) {
      console.error('게시글 작성 실패:', error);
      alert(
        `게시글 작성에 실패했습니다: ${error.message || '알 수 없는 오류'}`
      );
    }
  };

  const handleLike = useCallback(async (postId) => {
    const snapshot = prevPostsRef.current;

    setPosts((currentPosts) =>
      currentPosts.map((post) => {
        if ((post.postId || post.id) === postId) {
          return {
            ...post,
            isLiked: !post.isLiked,
            likeCount: post.isLiked ? post.likeCount - 1 : post.likeCount + 1,
          };
        }
        return post;
      })
    );

    try {
      await boardApi.toggleLike(postId);
    } catch (error) {
      console.error('좋아요 처리 실패:', error);
      alert('좋아요 처리에 실패했습니다.');
      setPosts(snapshot);
    }
  }, []);

  const handleBookmark = useCallback(async (postId) => {
    const snapshot = prevPostsRef.current;

    setPosts((currentPosts) =>
      currentPosts.map((post) => {
        if ((post.postId || post.id) === postId) {
          return {
            ...post,
            isBookmarked: !post.isBookmarked,
            bookmarkCount: post.isBookmarked
              ? (post.bookmarkCount || 1) - 1
              : (post.bookmarkCount || 0) + 1,
          };
        }
        return post;
      })
    );

    try {
      await boardApi.toggleBookmark(postId);
    } catch (error) {
      console.error('북마크 처리 실패:', error);
      alert('북마크 처리에 실패했습니다.');
      setPosts(snapshot);
    }
  }, []);

  const sortedPosts = [...posts].sort((a, b) => {
    if (sortOption === 'latest') {
      const dateA = new Date(a.createdDate || a.date);
      const dateB = new Date(b.createdDate || b.date);
      return dateB - dateA;
    }
    if (sortOption === 'oldest') {
      const dateA = new Date(a.createdDate || a.date);
      const dateB = new Date(b.createdDate || b.date);
      return dateA - dateB;
    }
    if (sortOption === 'popular') {
      return b.likeCount - a.likeCount;
    }
    return 0;
  });

  if (!boardsLoaded) {
    return (
      <div className={styles.boardContainer}>
        <p className={styles.emptyMessage}>게시판 목록을 불러오는 중...</p>
      </div>
    );
  }

  if (!currentBoardId) {
    return (
      <div className={styles.boardContainer}>
        <header className={styles.boardHeader}>
          <h1 className={styles.boardTitle}>게시판</h1>
        </header>
        <p className={styles.emptyMessage}>
          게시판을 찾을 수 없습니다. (경로: {currentPath})
        </p>
      </div>
    );
  }

  return (
    <div className={styles.boardContainer}>
      <header className={styles.boardHeader}>
        <h1 className={styles.boardTitle}>{currentBoardName || '게시판'}</h1>
      </header>

      <SearchBar searchTerm={searchTerm} onSearchChange={setSearchTerm} />

      {!Array.isArray(currentBoardId) && (
        <CategoryTabs
          activeTab={activeSubBoard}
          onTabChange={handleTabChange}
          tabs={subBoardTabs}
          onCreateSubBoard={handleOpenSubBoardModal}
        />
      )}

      <BoardActions
        sortOption={sortOption}
        onSortChange={setSortOption}
        onWrite={handleOpenModal}
      />

      <div className={styles.postsContainer}>
        {loading ? (
          <p className={styles.emptyMessage}>로딩 중...</p>
        ) : sortedPosts.length > 0 ? (
          sortedPosts.map((post) => (
            <PostItem
              key={post.postId || post.id}
              post={post}
              currentTeam={team}
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
          selectedFiles={selectedFiles}
          onFileChange={handleFileChange}
          onRemoveFile={handleRemoveFile}
          onSave={handleSave}
          onClose={handleCloseModal}
        />
      )}

      {showSubBoardModal && (
        <CreateSubBoardModal
          value={subBoardName}
          onChange={setSubBoardName}
          onSave={handleCreateSubBoard}
          onClose={handleCloseSubBoardModal}
        />
      )}
    </div>
  );
};

export default Board;
