import { api } from '../utils/axios.js';

const betHitsory = async () => {
  try {
    const res = await api.get('/api/user-bets/history');
    return res.data;
  } catch (error) {
    console.log(error.message);
  }
};

export const getDailyBetHistory = async () => {
  const data = await betHitsory();
  if (!data) return [];
  return data.filter((item) => item.round.scope === 'DAILY');
};

export const getWeeklyBetHistory = async () => {
  const data = await betHitsory();
  if (!data) return [];
  return data.filter((item) => item.round.scope === 'WEEKLY');
};
