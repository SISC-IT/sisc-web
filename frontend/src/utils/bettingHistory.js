import { api } from '../utils/axios.js';

const betHistory = async () => {
  try {
    const res = await api.get('/api/user-bets/history');
    return res.data;
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
