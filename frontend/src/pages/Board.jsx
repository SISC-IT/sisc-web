import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import PostItem from '../components/Board/PostItem';
import Modal from '../components/Board/Modal';
import SearchBar from '../components/Board/SearchBar';
import BoardActions from '../components/Board/BoardActions';
import CategoryTabs from '../components/Board/CategoryTabs';
import CreateSubBoardModal from '../components/Board/CreateSubBoardModal';
import styles from './Board.module.css';
import * as boardApi from '../utils/boardApi';
import {
  isAllBoardName,
  normalizeBoardRouteSegment,
  toBoardRouteSegment,
} from '../utils/boardRoute';
import { api } from '../utils/axios';

const ALL_TAB_ID = 'all';
const SUB_BOARD_ADMIN_ROLES = ['SYSTEM_ADMIN', 'PRESIDENT', 'VICE_PRESIDENT'];

const getPostId = (post) => post?.postId || post?.id;

const mergePosts = (responses = []) => {
  const map = new Map();

  responses.forEach((response) => {
    (response?.content || []).forEach((post) => {
      const id = getPostId(post);
      if (!id) return;
      if (!map.has(id)) {
        map.set(id, post);
      }
    });
  });

  return Array.from(map.values());
};

const Board = () => {
  const { team } = useParams();
  const location = useLocation();

  const [boardIdMap, setBoardIdMap] = useState({});
  const [boardNameMap, setBoardNameMap] = useState({});
  const [posts, setPosts] = useState([]);

  const [showModal, setShowModal] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [sortOption, setSortOption] = useState('latest');
  const [loading, setLoading] = useState(false);
  const [boardsLoaded, setBoardsLoaded] = useState(false);
  const [activeSubBoard, setActiveSubBoard] = useState(ALL_TAB_ID);
  const [showSubBoardModal, setShowSubBoardModal] = useState(false);
  const [subBoardName, setSubBoardName] = useState('');
  const [subBoardTabs, setSubBoardTabs] = useState([{ id: ALL_TAB_ID, name: '전체 게시판' }]);
  const [writeBoardId, setWriteBoardId] = useState('');
  const [canCreateSubBoard, setCanCreateSubBoard] = useState(false);
  const [isSavingPost, setIsSavingPost] = useState(false);

  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 4;

  const prevPostsRef = useRef(posts);
  const requestedSubBoardId = useMemo(() => {
    const params = new URLSearchParams(location.search);
    return params.get('subBoardId') || '';
  }, [location.search]);

  const currentSegment = team ? normalizeBoardRouteSegment(team) || 'root' : 'root';
  const currentBoardId = boardIdMap[currentSegment];
  const currentBoardName = boardNameMap[currentSegment] || '게시판';

  useEffect(() => {
    prevPostsRef.current = posts;
  }, [posts]);

  const loadBoardList = useCallback(async () => {
    try {
      const boards = await boardApi.getParentBoards();

      if (!boards || boards.length === 0) {
        setBoardIdMap({});
        setBoardNameMap({});
        setBoardsLoaded(true);
        return;
      }

      const idMap = {};
      const nameMap = {};

      boards.forEach((board) => {
        const boardName = String(board.boardName || '').trim();
        const segment = isAllBoardName(boardName)
          ? 'root'
          : toBoardRouteSegment(boardName);

        if (!segment) return;

        idMap[segment] = board.boardId;
        nameMap[segment] = boardName;
      });

      setBoardIdMap(idMap);
      setBoardNameMap(nameMap);
      setBoardsLoaded(true);
    } catch (error) {
      console.error('게시판 목록 불러오기 실패:', error);
      setBoardsLoaded(true);
    }
  }, []);

  useEffect(() => {
    loadBoardList();
  }, [loadBoardList]);

  useEffect(() => {
    const fetchRole = async () => {
      try {
        const { data } = await api.get('/api/user/details');
        const normalizedRole = String(data?.role || '').trim().toUpperCase();
        setCanCreateSubBoard(SUB_BOARD_ADMIN_ROLES.includes(normalizedRole));
      } catch {
        setCanCreateSubBoard(false);
      }
    };

    fetchRole();
  }, []);

  const fetchPostsByBoardIds = useCallback(async (boardIds, keyword = '') => {
    const uniqueBoardIds = Array.from(new Set((boardIds || []).filter(Boolean)));

    if (uniqueBoardIds.length === 0) {
      setPosts([]);
      setCurrentPage(1);
      return;
    }

    try {
      setLoading(true);

      const requests = uniqueBoardIds.map((boardId) =>
        keyword && keyword.trim()
          ? boardApi.searchPosts(boardId, keyword).catch(() => ({ content: [] }))
          : boardApi.getPosts(boardId).catch(() => ({ content: [] }))
      );

      const responses = await Promise.all(requests);
      const merged = mergePosts(responses);
      setPosts(merged);
      setCurrentPage(1);
    } catch (error) {
      console.error('게시글 조회 실패:', error);
      setPosts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const getBoardIdsForTab = useCallback(
    (tabId) => {
      if (!currentBoardId) return [];

      if (tabId && tabId !== ALL_TAB_ID) {
        return [tabId];
      }

      const childBoardIds = subBoardTabs
        .filter((tab) => tab.id !== ALL_TAB_ID)
        .map((tab) => tab.id);

      return [currentBoardId, ...childBoardIds];
    },
    [currentBoardId, subBoardTabs]
  );

  useEffect(() => {
    const loadSubBoardsAndPosts = async () => {
      if (!boardsLoaded || !currentBoardId) return;

      try {
        const subBoards = await boardApi.getSubBoards(currentBoardId);
        const tabs = [{ id: ALL_TAB_ID, name: '전체 게시판' }];

        if (Array.isArray(subBoards) && subBoards.length > 0) {
          tabs.push(
            ...subBoards.map((board) => ({
              id: board.boardId,
              name: board.boardName,
            }))
          );
        }

        setSubBoardTabs(tabs);

        const isRequestedSubBoardValid =
          requestedSubBoardId && tabs.some((tab) => tab.id === requestedSubBoardId);
        const nextTabId = isRequestedSubBoardValid ? requestedSubBoardId : ALL_TAB_ID;

        setActiveSubBoard(nextTabId);

        const targetBoardIds =
          nextTabId === ALL_TAB_ID
            ? [currentBoardId, ...tabs.filter((tab) => tab.id !== ALL_TAB_ID).map((tab) => tab.id)]
            : [nextTabId];

        await fetchPostsByBoardIds(targetBoardIds);
      } catch (error) {
        console.error('하위 게시판 조회 실패:', error);
        setSubBoardTabs([{ id: ALL_TAB_ID, name: '전체 게시판' }]);
        setActiveSubBoard(ALL_TAB_ID);
        await fetchPostsByBoardIds([currentBoardId]);
      }
    };

    loadSubBoardsAndPosts();
  }, [boardsLoaded, currentBoardId, fetchPostsByBoardIds, requestedSubBoardId]);

  const handleTabChange = useCallback(
    async (tabId) => {
      setActiveSubBoard(tabId);
      const targetBoardIds = getBoardIdsForTab(tabId);
      await fetchPostsByBoardIds(targetBoardIds);
    },
    [fetchPostsByBoardIds, getBoardIdsForTab]
  );

  const handleSearch = useCallback(
    async (keyword) => {
      const targetBoardIds = getBoardIdsForTab(activeSubBoard);
      await fetchPostsByBoardIds(targetBoardIds, keyword);
    },
    [activeSubBoard, fetchPostsByBoardIds, getBoardIdsForTab]
  );

  const handleOpenModal = () => {
    setWriteBoardId('');
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setTitle('');
    setContent('');
    setSelectedFiles([]);
    setWriteBoardId('');
    setIsSavingPost(false);
  };

  const handleOpenSubBoardModal = () => {
    if (!canCreateSubBoard) {
      alert('하위 게시판 생성 권한이 없습니다.');
      return;
    }

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

    if (!currentBoardId) {
      alert('부모 게시판 정보를 찾을 수 없습니다.');
      return;
    }

    try {
      const created = await boardApi.createSubBoard(
        subBoardName.trim(),
        currentBoardId
      );
      handleCloseSubBoardModal();

      const subBoards = await boardApi.getSubBoards(currentBoardId);
      const tabs = [
        { id: ALL_TAB_ID, name: '전체 게시판' },
        ...(Array.isArray(subBoards)
          ? subBoards.map((board) => ({ id: board.boardId, name: board.boardName }))
          : []),
      ];

      setSubBoardTabs(tabs);

      const nextTabId = created?.boardId || ALL_TAB_ID;
      setActiveSubBoard(nextTabId);

      const targetBoardIds =
        nextTabId === ALL_TAB_ID
          ? [currentBoardId, ...tabs.filter((tab) => tab.id !== ALL_TAB_ID).map((tab) => tab.id)]
          : [nextTabId];

      await fetchPostsByBoardIds(targetBoardIds);
      alert('하위 게시판이 생성되었습니다!');
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
    if (isSavingPost) {
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

    if (!writeBoardId) {
      alert('세션을 선택해야 합니다.');
      return;
    }

    try {
      setIsSavingPost(true);

      const postData = {
        title: title.trim(),
        content: content.trim(),
        files: selectedFiles,
      };

      await boardApi.createPost(writeBoardId, postData);

      handleCloseModal();
      setSelectedFiles([]);

      const reloadBoardIds = getBoardIdsForTab(activeSubBoard);
      await fetchPostsByBoardIds(reloadBoardIds);

      alert('게시글이 작성되었습니다!');
    } catch (error) {
      console.error('게시글 작성 실패:', error);
      alert(`게시글 작성에 실패했습니다: ${error.message || '알 수 없는 오류'}`);
    } finally {
      window.setTimeout(() => {
        setIsSavingPost(false);
      }, 800);
    }
  };

  const handleLike = useCallback(async (postId) => {
    const snapshot = prevPostsRef.current;

    setPosts((currentPosts) =>
      currentPosts.map((post) => {
        if (getPostId(post) === postId) {
          return {
            ...post,
            isLiked: !post.isLiked,
            likeCount: post.isLiked
              ? Math.max((post.likeCount || 1) - 1, 0)
              : (post.likeCount || 0) + 1,
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
        if (getPostId(post) === postId) {
          return {
            ...post,
            isBookmarked: !post.isBookmarked,
            bookmarkCount: post.isBookmarked
              ? Math.max((post.bookmarkCount || 1) - 1, 0)
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

  const handleSortChange = (option) => {
    setSortOption(option);
    setCurrentPage(1);
  };

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
      return (b.likeCount || 0) - (a.likeCount || 0);
    }
    return 0;
  });

  const indexOfLastPost = currentPage * itemsPerPage;
  const indexOfFirstPost = indexOfLastPost - itemsPerPage;
  const currentPosts = sortedPosts.slice(indexOfFirstPost, indexOfLastPost);
  const totalPages = Math.ceil(sortedPosts.length / itemsPerPage);

  const handlePageChange = (pageNumber) => {
    setCurrentPage(pageNumber);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

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
        <p className={styles.emptyMessage}>게시판을 찾을 수 없습니다. (경로: {currentSegment})</p>
      </div>
    );
  }

  return (
    <div className={styles.boardContainer}>
      <header className={styles.boardHeader}>
        <h1 className={styles.boardTitle}>{currentBoardName || '게시판'}</h1>
      </header>

      <SearchBar onSearch={handleSearch} />

      <CategoryTabs
        activeTab={activeSubBoard}
        onTabChange={handleTabChange}
        tabs={subBoardTabs}
        onCreateSubBoard={handleOpenSubBoardModal}
        canCreateSubBoard={canCreateSubBoard}
      />

      <BoardActions
        sortOption={sortOption}
        onSortChange={handleSortChange}
        onWrite={handleOpenModal}
        resultCount={sortedPosts.length}
      />

      <div className={styles.postsContainer}>
        {loading ? (
          <p className={styles.emptyMessage}>로딩 중...</p>
        ) : currentPosts.length > 0 ? (
          currentPosts.map((post) => (
            <PostItem
              key={getPostId(post)}
              post={post}
              onLike={handleLike}
              onBookmark={handleBookmark}
            />
          ))
        ) : (
          <p className={styles.emptyMessage}>게시글이 없습니다.</p>
        )}
      </div>

      {!loading && sortedPosts.length > 0 && (
        <div className={styles.pagination}>
          <button
            onClick={() => handlePageChange(currentPage - 1)}
            disabled={currentPage === 1}
            className={styles.pageButton}
          >
            이전
          </button>

          {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
            <button
              key={page}
              onClick={() => handlePageChange(page)}
              className={`${styles.pageButton} ${currentPage === page ? styles.active : ''}`}
            >
              {page}
            </button>
          ))}

          <button
            onClick={() => handlePageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
            className={styles.pageButton}
          >
            다음
          </button>
        </div>
      )}

      {showModal && (
        <Modal
          title={title}
          setTitle={setTitle}
          content={content}
          setContent={setContent}
          boardOptions={subBoardTabs.filter((tab) => tab.id !== ALL_TAB_ID)}
          selectedBoardId={writeBoardId}
          onBoardChange={setWriteBoardId}
          selectedFiles={selectedFiles}
          onFileChange={handleFileChange}
          onRemoveFile={handleRemoveFile}
          onSave={handleSave}
          onClose={handleCloseModal}
          isSaving={isSavingPost}
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
