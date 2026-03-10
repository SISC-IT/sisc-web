import axios from 'axios';

export const getUserPoints = async ({ pageNumber = 0, pageSize = 10 }) => {
  try {
    const response = await axios.get(`/api/points/history`, {
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
