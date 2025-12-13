// boardApi.js
import { api } from './axios';

// ==================== 게시판 API ====================

/*
 * 최상위 부모 게시판 목록 조회
 * GET /api/board/parents
 */
export const getParentBoards = async () => {
  const response = await api.get('/api/board/parents');
  return response.data;
};

/*
 * 하위 게시판 목록 조회
 * GET /api/board/childs
 * @param {string|null} parentBoardId - 특정 부모 게시판의 자식만 필터링 (선택)
 */
export const getSubBoards = async (parentBoardId = null) => {
  const response = await api.get('/api/board/childs');

  // parentBoardId가 제공되면 해당 부모의 자식만 필터링
  if (parentBoardId) {
    return response.data.filter(
      (board) => board.parentBoardId === parentBoardId
    );
  }

  // 제공되지 않으면 모든 하위 게시판 반환
  return response.data;
};

/*
 * 게시판 생성
 * POST /api/board
 * @param {string} boardName - 게시판 이름
 * @param {string|null} parentBoardId - 부모 게시판 ID (최상위는 null)
 */
export const createBoard = async (boardName, parentBoardId = null) => {
  const requestBody = { boardName };

  if (parentBoardId) {
    requestBody.parentBoardId = parentBoardId;
  }

  const response = await api.post('/api/board', requestBody);
  return response.data;
};

// ==================== 게시글 API ====================

/*
 * 게시글 목록 조회
 * GET /api/board/posts
 * @param {string} boardId - 게시판 ID
 * @param {number} pageNumber - 페이지 번호 (0부터 시작)
 * @param {number} pageSize - 페이지 크기
 */
export const getPosts = async (boardId, pageNumber = 0, pageSize = 20) => {
  const response = await api.get('/api/board/posts', {
    params: { boardId, pageNumber, pageSize },
  });
  return response.data;
};

/*
 * 게시글 검색
 * GET /api/board/posts/search
 * @param {string} boardId - 게시판 ID
 * @param {string} keyword - 검색 키워드
 * @param {number} pageNumber - 페이지 번호
 * @param {number} pageSize - 페이지 크기
 */
export const searchPosts = async (
  boardId,
  keyword,
  pageNumber = 0,
  pageSize = 20
) => {
  const response = await api.get('/api/board/posts/search', {
    params: { boardId, keyword, pageNumber, pageSize },
  });
  return response.data;
};

/*
 * 게시글 작성
 * POST /api/board/post
 * @param {string} boardId - 게시판 ID
 * @param {object} postData - { title, content, files }
 */
export const createPost = async (boardId, postData) => {
  if (!postData?.title) throw new Error('제목이 없습니다.');
  if (!postData?.content) throw new Error('내용이 없습니다.');

  const formData = new FormData();
  formData.append('boardId', boardId);
  formData.append('title', postData.title);
  formData.append('content', postData.content);

  if (postData.files && postData.files.length > 0) {
    postData.files.forEach((file) => {
      formData.append('files', file);
    });
  }

  const response = await api.post('/api/board/post', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

/*
 * 게시글 상세 조회
 * GET /api/board/post/{postId}
 * @param {string} postId - 게시글 ID
 * @param {number} commentPageNumber - 댓글 페이지 번호
 * @param {number} commentPageSize - 댓글 페이지 크기
 */
export const getPost = async (
  postId,
  commentPageNumber = 0,
  commentPageSize = 20
) => {
  const response = await api.get(`/api/board/post/${postId}`, {
    params: { commentPageNumber, commentPageSize },
  });
  return response.data;
};

/*
 * 게시글 수정
 * PUT /api/board/post/{postId}
 * @param {string} postId - 게시글 ID
 * @param {string} boardId - 게시판 ID
 * @param {object} postData - { title, content, files }
 */
export const updatePost = async (postId, boardId, postData) => {
  if (!boardId) {
    console.error('boardId가 없습니다.');
    throw new Error('boardId is required');
  }

  const formData = new FormData();
  formData.append('boardId', boardId);
  formData.append('title', postData.title);
  formData.append('content', postData.content);

  if (postData.files && postData.files.length > 0) {
    postData.files.forEach((file) => {
      formData.append('files', file);
    });
  }

  const response = await api.put(`/api/board/post/${postId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

/*
 * 게시글 삭제
 * DELETE /api/board/post/{postId}
 * @param {string} postId - 게시글 ID
 */
export const deletePost = async (postId) => {
  const response = await api.delete(`/api/board/post/${postId}`);
  return response.data;
};

// ==================== 좋아요/북마크 API ====================

/*
 * 좋아요 토글
 * POST /api/board/{postId}/like
 * @param {string} postId - 게시글 ID
 */
export const toggleLike = async (postId) => {
  const response = await api.post(`/api/board/${postId}/like`);
  return response.data;
};

/*
 * 북마크 토글
 * POST /api/board/{postId}/bookmark
 * @param {string} postId - 게시글 ID
 */
export const toggleBookmark = async (postId) => {
  const response = await api.post(`/api/board/${postId}/bookmark`);
  return response.data;
};

// ==================== 댓글 API ====================

/*
 * 댓글 작성
 * POST /api/board/comment
 * @param {object} commentData - { postId, content, parentCommentId? }
 */
export const createComment = async (commentData) => {
  const requestBody = {
    postId: commentData.postId,
    content: commentData.content,
  };

  if (commentData.parentCommentId) {
    requestBody.parentCommentId = commentData.parentCommentId;
  }

  const response = await api.post('/api/board/comment', requestBody);
  return response.data;
};

/*
 * 댓글 수정
 * PUT /api/board/comment/{commentId}
 * @param {string} commentId - 댓글 ID
 * @param {object} commentData - { postId, content, parentCommentId? }
 */
export const updateComment = async (commentId, commentData) => {
  const body = {
    postId: commentData.postId,
    content: commentData.content,
    parentCommentId: commentData.parentCommentId ?? null,
  };

  const response = await api.put(`/api/board/comment/${commentId}`, body);
  return response.data;
};

/*
 * 댓글 삭제
 * DELETE /api/board/comment/{commentId}
 * @param {string} commentId - 댓글 ID
 */
export const deleteComment = async (commentId) => {
  const response = await api.delete(`/api/board/comment/${commentId}`);
  return response.data;
};

export default api;
