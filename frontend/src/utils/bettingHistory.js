import { api } from '../utils/axios.js';

const betHistory = async () => {
  try {
    const res = await api.get('/api/user-bets/history');
    // 백엔드에서 최신순으로 정렬된 데이터 반환
    const filterdRes = res.data.filter((r) => r.betStatus !== 'DELETED');
    return filterdRes;
  } catch (error) {
    console.log(error.message);
    return null;
  }
};

export const getDailyBetHistory = async () => {
  const data = await betHistory();
  if (!data) return [];
  return data.filter((item) => item.roundTitle.includes('DAILY'));
};

export const getWeeklyBetHistory = async () => {
  const data = await betHistory();
  if (!data) return [];
  return data.filter((item) => item.roundTitle.includes('WEEKLY'));
};
