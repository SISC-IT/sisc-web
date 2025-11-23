import React, { useState, useEffect } from 'react';
import { useLocation, useParams } from 'react-router-dom';
import PostItem from '../components/Board/PostItem';
import Modal from '../components/Board/Modal';
import SearchBar from '../components/Board/SearchBar';
import BoardActions from '../components/Board/BoardActions';
import styles from './Board.module.css';
import * as boardApi from '../utils/boardApi';

const Board = () => {
  const location = useLocation();
  const { team } = useParams(); // ✨ boardType → team

  // State 관리
  const [boardIdMap, setBoardIdMap] = useState({});
  const [boardNameMap, setBoardNameMap] = useState({});
  const [posts, setPosts] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [sortOption, setSortOption] = useState('latest');
  const [loading, setLoading] = useState(false);
  const [boardsLoaded, setBoardsLoaded] = useState(false);

  // ✨ team 사용
  const currentPath = team ? `/board/${team}` : '/board';
  const currentBoardId = boardIdMap[currentPath];
  const currentBoardName = boardNameMap[currentPath];

  // 컴포넌트 로드 시 게시판 목록 불러오기
  useEffect(() => {
    loadBoardList();
  }, []);

  // 서버에서 게시판 목록을 불러와서 path와 매핑
  const loadBoardList = async () => {
    try {
      console.log('게시판 목록 불러오는 중...');
      const boards = await boardApi.getParentBoards();
      console.log('서버에서 받은 게시판 목록:', boards);

      if (!boards || boards.length === 0) {
        console.warn('게시판이 없습니다.');
        alert('등록된 게시판이 없습니다.');
        setBoardsLoaded(true);
        return;
      }

      const idMap = {};
      const nameMap = {};

      const pathMapping = {
        '증권1팀 게시판': '/board/securities-1',
        '증권2팀 게시판': '/board/securities-2',
        '증권3팀 게시판': '/board/securities-3',
        '자산운용팀 게시판': '/board/asset-management',
        '금융IT팀 게시판': '/board/finance-it',
        '매크로팀 게시판': '/board/macro',
        '트레이딩팀 게시판': '/board/trading',
      };

      boards.forEach((board) => {
        const path = pathMapping[board.boardName];

        if (path) {
          idMap[path] = board.boardId;
          nameMap[path] = board.boardName;
        }
      });

      // 전체 게시판
      idMap['/board'] = boards.map((b) => b.boardId);

      console.log('생성된 ID 매핑:', idMap);
      console.log('생성된 이름 매핑:', nameMap);

      setBoardIdMap(idMap);
      setBoardNameMap(nameMap);
      setBoardsLoaded(true);
    } catch (error) {
      console.error('게시판 목록 불러오기 실패:', error);
      setBoardsLoaded(true);
    }
  };

  const fetchPosts = async () => {
    if (!currentBoardId) {
      console.log('boardId가 없습니다. 경로:', currentPath);
      return;
    }

    try {
      setLoading(true);

      if (Array.isArray(currentBoardId)) {
        // 전체 게시판
        console.log('전체 게시판 조회 - 모든 게시판:', currentBoardId);

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
        // 개별 팀 게시판
        console.log(
          '게시글 조회 - 게시판:',
          currentBoardName,
          '/ ID:',
          currentBoardId
        );
        const response = await boardApi.getPosts(currentBoardId);
        console.log('받은 게시글:', response);

        const postsData = response.content || [];
        setPosts(Array.isArray(postsData) ? postsData : []);
      }
    } catch (error) {
      console.error('게시글 불러오기 실패:', error);
      console.error('에러 상세:', error.response);
      setPosts([]);
    } finally {
      setLoading(false);
    }
  };

  // team 의존성 추가
  useEffect(() => {
    if (boardsLoaded && currentBoardId) {
      fetchPosts();
    }
  }, [team, currentBoardId, boardsLoaded]);

  // 검색어 변경 시
  useEffect(() => {
    if (boardsLoaded && currentBoardId) {
      if (searchTerm) {
        handleSearch();
      } else {
        fetchPosts();
      }
    }
  }, [searchTerm]);

  // 게시글 검색
  const handleSearch = async () => {
    if (!currentBoardId) return;

    try {
      setLoading(true);

      // 전체 게시판 검색
      if (Array.isArray(currentBoardId)) {
        const allPostsPromises = currentBoardId.map((id) =>
          boardApi.searchPosts(id, searchTerm).catch(() => [])
        );

        const allPostsArrays = await Promise.all(allPostsPromises);
        const allPosts = allPostsArrays.flat();

        setPosts(allPosts);
      } else {
        const data = await boardApi.searchPosts(currentBoardId, searchTerm);
        setPosts(data);
      }
    } catch (error) {
      console.error('검색 실패:', error);
      alert('검색에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 모달 열기
  const handleOpenModal = () => {
    setShowModal(true);
  };

  // 모달 닫기
  const handleCloseModal = () => {
    setShowModal(false);
    setTitle('');
    setContent('');
  };

  // 게시글 작성
  const handleSave = async () => {
    console.log('=== 게시글 저장 시도 ===');
    console.log('title:', title);
    console.log('content:', content);
    console.log('현재 경로:', currentPath);
    console.log('현재 boardId:', currentBoardId);
    console.log('게시판 이름:', currentBoardName);

    if (Array.isArray(currentBoardId)) {
      alert(
        '전체 게시판에서는 글을 작성할 수 없습니다.\n특정 팀 게시판을 선택해주세요.'
      );
      return;
    }

    if (!currentBoardId) {
      alert(`게시판 정보를 찾을 수 없습니다.\n현재 경로: ${currentPath}`);
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
      };

      console.log('전송할 데이터:', { boardId: currentBoardId, ...postData });

      await boardApi.createPost(currentBoardId, postData);
      handleCloseModal();
      fetchPosts();
      alert('게시글이 작성되었습니다!');
    } catch (error) {
      console.error('게시글 작성 실패:', error);
      console.error('에러 상세:', error.response);
      alert('게시글 작성에 실패했습니다.');
    }
  };

  // 좋아요 토글
  const handleLike = async (postId) => {
    try {
      await boardApi.toggleLike(postId);
      fetchPosts();
    } catch (error) {
      console.error('좋아요 처리 실패:', error);
      alert('좋아요 처리에 실패했습니다.');
    }
  };

  // 북마크 토글
  const handleBookmark = async (postId) => {
    try {
      await boardApi.toggleBookmark(postId);
      fetchPosts();
    } catch (error) {
      console.error('북마크 처리 실패:', error);
      alert('북마크 처리에 실패했습니다.');
    }
  };

  // 게시글 정렬
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

  // 게시판 목록이 로드되지 않았으면 로딩 표시
  if (!boardsLoaded) {
    return (
      <div className={styles.boardContainer}>
        <p className={styles.emptyMessage}>게시판 목록을 불러오는 중...</p>
      </div>
    );
  }

  // 현재 경로에 해당하는 게시판이 없으면
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
      {/* 게시판 헤더 */}
      <header className={styles.boardHeader}>
        <h1 className={styles.boardTitle}>{currentBoardName || '게시판'}</h1>
      </header>

      {/* 검색 및 정렬 컨트롤 */}
      <div className={styles.boardControls}>
        <SearchBar searchTerm={searchTerm} onSearchChange={setSearchTerm} />
        <BoardActions
          sortOption={sortOption}
          onSortChange={setSortOption}
          onWrite={handleOpenModal}
        />
      </div>

      {/* 게시글 목록 */}
      <div className={styles.postsContainer}>
        {loading ? (
          <p className={styles.emptyMessage}>로딩 중...</p>
        ) : sortedPosts.length > 0 ? (
          sortedPosts.map((post) => (
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

      {/* 게시글 작성 모달 */}
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
