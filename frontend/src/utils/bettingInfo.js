import { api } from '../utils/axios.js';

export const dailyBet = async () => {
  try {
    const res = await api.get('/api/bet-rounds/DAILY');
    return res.data;
  } catch (error) {
    console.log(error.message);
  }
};

export const weeklyBet = async () => {
  try {
    const res = await api.get('/api/bet-rounds/WEEKLY');
    return res.data;
  } catch (error) {
    console.log(error.message);
  }
};
