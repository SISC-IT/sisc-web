import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL;

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 최상위 부모 게시판 목록 조회
export const getParentBoards = async () => {
  const response = await apiClient.get('/api/board/parents');
  return response.data;
};

// 게시판 생성 함수
export const createBoard = async (boardName, parentBoardId = null) => {
  const requestBody = {
    boardName: boardName,
  };

  // parentBoardId가 있으면 추가
  if (parentBoardId) {
    requestBody.parentBoardId = parentBoardId;
  }

  const response = await apiClient.post('/api/board', requestBody);
  return response.data;
};

// 게시글 목록 조회 - boardId 파라미터 추가
export const getPosts = async (boardId) => {
  const response = await apiClient.get('/api/board/posts', {
    params: { boardId }, // 쿼리 파라미터로 전달
  });
  return response.data;
};

// 게시글 검색 - boardId 파라미터 추가
export const searchPosts = async (boardId, searchTerm) => {
  const response = await apiClient.get('/api/board/posts/search', {
    params: {
      boardId,
      keyword: searchTerm,
    },
  });
  return response.data;
};

// 게시글 작성 - boardId 포함
export const createPost = async (boardId, postData) => {
  console.log('createPost 호출됨 - boardId:', boardId); // ✨ 디버깅 로그
  console.log('createPost 호출됨 - postData:', postData); // ✨ 디버깅 로그

  if (!postData) {
    throw new Error('postData가 없습니다.');
  }

  if (!postData.title) {
    throw new Error('title이 없습니다.');
  }

  if (!postData.content) {
    throw new Error('content가 없습니다.');
  }

  const formData = new FormData();
  formData.append('boardId', boardId);
  formData.append('title', postData.title);
  formData.append('content', postData.content);

  if (postData.files) {
    postData.files.forEach((file) => {
      formData.append('files', file);
    });
  }

  const response = await apiClient.post('/api/board/post', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
};

// 게시글 상세 조회
export const getPost = async (postId) => {
  const response = await apiClient.get(`/api/board/post/${postId}`);
  return response.data;
};

// 게시글 수정
export const updatePost = async (postId, postData) => {
  const response = await apiClient.put(`/api/board/post/${postId}`, postData);
  return response.data;
};

// 게시글 삭제
export const deletePost = async (postId) => {
  const response = await apiClient.delete(`/api/board/post/${postId}`);
  return response.data;
};

// 좋아요 토글
export const toggleLike = async (postId) => {
  const response = await apiClient.post(`/api/board/${postId}/like`);
  return response.data;
};

// 북마크 토글
export const toggleBookmark = async (postId) => {
  const response = await apiClient.post(`/api/board/${postId}/bookmark`);
  return response.data;
};

// 댓글 작성
export const createComment = async (commentData) => {
  const response = await apiClient.post('/api/board/comment', commentData);
  return response.data;
};

// 댓글 수정
export const updateComment = async (commentId, commentData) => {
  const response = await apiClient.put(
    `/api/board/comment/${commentId}`,
    commentData
  );
  return response.data;
};

// 댓글 삭제
export const deleteComment = async (commentId) => {
  const response = await apiClient.delete(`/api/board/comment/${commentId}`);
  return response.data;
};

export default apiClient;
