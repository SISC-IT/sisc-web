import { api } from '../utils/axios.js';
import { toast } from 'react-toastify';

export const dailyBet = async () => {
  try {
    const res = await api.get('/api/bet-rounds/DAILY');
    return res.data;
  } catch {
    toast.error('오류가 발생했습니다. 다시 시도해주세요.');
    return null;
  }
};

export const weeklyBet = async () => {
  try {
    const res = await api.get('/api/bet-rounds/WEEKLY');
    return res.data;
  } catch {
    toast.error('오류가 발생했습니다. 다시 시도해주세요.');
    return null;
  }
};
