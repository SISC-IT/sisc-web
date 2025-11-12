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

export const mockDailyBet = {
  date: '2025-08-25',
  symbol: 'AAPL',
  closePrice: 96.3,
  upperBet: 300,
  lowerBet: 300,
  upBetCount: 200,
  downBetCount: 200,
};

export const mockWeeklyBet = {
  date: '2025-08-18',
  symbol: 'AAPL',
  closePrice: 98.5,
  upperBet: 350,
  lowerBet: 300,
  upBetCount: 150,
  downBetCount: 230,
};
