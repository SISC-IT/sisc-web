// boardApi.js
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL;

// ==================== Axios 인스턴스 설정 ====================

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 요청 인터셉터: JWT 자동 추가
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터: 401 에러 시 토큰 갱신
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');

        if (!refreshToken) {
          throw new Error('No refresh token available');
        }

        const response = await axios.post(
          `${API_BASE_URL}/api/auth/reissue`,
          { refreshToken },
          { withCredentials: true }
        );

        const newAccessToken = response.data.accessToken;
        localStorage.setItem('accessToken', newAccessToken);
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

        return apiClient(originalRequest);
      } catch (refreshError) {
        console.error('토큰 갱신 실패:', refreshError);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    const status = error.response?.status;
    const message =
      error.response?.data?.message ||
      error.response?.statusText ||
      '오류가 발생했습니다.';

    return Promise.reject({ status, message, data: error.response?.data });
  }
);

// ==================== 게시판 API ====================

/*
 * 최상위 부모 게시판 목록 조회
 * GET /api/board/parents
 */
export const getParentBoards = async () => {
  const response = await apiClient.get('/api/board/parents');
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

  const response = await apiClient.post('/api/board', requestBody);
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
  const response = await apiClient.get('/api/board/posts', {
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
  const response = await apiClient.get('/api/board/posts/search', {
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

  const response = await apiClient.post('/api/board/post', formData, {
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
  const response = await apiClient.get(`/api/board/post/${postId}`, {
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
  const formData = new FormData();
  formData.append('boardId', boardId);
  formData.append('title', postData.title);
  formData.append('content', postData.content);

  if (postData.files && postData.files.length > 0) {
    postData.files.forEach((file) => {
      formData.append('files', file);
    });
  }

  const response = await apiClient.put(`/api/board/post/${postId}`, formData, {
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
  const response = await apiClient.delete(`/api/board/post/${postId}`);
  return response.data;
};

// ==================== 좋아요/북마크 API ====================

/*
 * 좋아요 토글
 * POST /api/board/{postId}/like
 * @param {string} postId - 게시글 ID
 */
export const toggleLike = async (postId) => {
  const response = await apiClient.post(`/api/board/${postId}/like`);
  return response.data;
};

/*
 * 북마크 토글
 * POST /api/board/{postId}/bookmark
 * @param {string} postId - 게시글 ID
 */
export const toggleBookmark = async (postId) => {
  const response = await apiClient.post(`/api/board/${postId}/bookmark`);
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

  const response = await apiClient.post('/api/board/comment', requestBody);
  return response.data;
};

/*
 * 댓글 수정
 * PUT /api/board/comment/{commentId}
 * @param {string} commentId - 댓글 ID
 * @param {object} commentData - { postId, content, parentCommentId? }
 */
export const updateComment = async (commentId, commentData) => {
  const requestBody = {
    postId: commentData.postId,
    content: commentData.content,
  };

  if (commentData.parentCommentId) {
    requestBody.parentCommentId = commentData.parentCommentId;
  }

  const response = await apiClient.put(
    `/api/board/comment/${commentId}`,
    requestBody
  );
  return response.data;
};

/*
 * 댓글 삭제
 * DELETE /api/board/comment/{commentId}
 * @param {string} commentId - 댓글 ID
 */
export const deleteComment = async (commentId) => {
  const response = await apiClient.delete(`/api/board/comment/${commentId}`);
  return response.data;
};

export default apiClient;
