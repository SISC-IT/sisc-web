import { api } from './axios.js';

export const getUserPoints = async ({ pageNumber = 0, pageSize = 10 }) => {
  try {
    const response = await api.get(`/api/points/history`, {
      params: {
        pageNumber,
        pageSize,
      },
      withCredentials: true, // 인증 필요 시
    });
    return response.data.history;
  } catch (error) {
    console.error('포인트 내역 조회 실패', error);
    throw error;
  }
};

export const getActivityLogs = async (page, size) => {
  try {
    const response = await api.get('/api/user/logs/board', {
      params: {
        page,
        size,
      },
    });

    return response.data;
  } catch (error) {
    console.error('활동 로그 조회 실패', error);
    throw error;
  }
};
