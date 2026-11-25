import { api } from './axios.js';

// 실패하면 axios 인터셉터가 만든 { status, message, data } 를 그대로 던진다.
// 화면마다 에러 코드(data.errorCode)에 따라 안내가 달라서 여기서 토스트를 띄우지 않는다.

export const getCurrentCompetition = async () => {
  const res = await api.get('/api/competitions/current');
  return res.data;
};

export const searchStocks = async (
  competitionId,
  { query, page = 0, size = 20 } = {}
) => {
  const res = await api.get(`/api/competitions/${competitionId}/stocks`, {
    params: { market: 'KR', query: query || undefined, page, size },
  });
  return res.data;
};

export const getStockDetail = async (competitionId, ticker) => {
  const res = await api.get(
    `/api/competitions/${competitionId}/stocks/${ticker}`
  );
  return res.data;
};

export const placeOrder = async (competitionId, order, idempotencyKey) => {
  const res = await api.post(
    `/api/competitions/${competitionId}/me/trades`,
    order,
    {
      headers: { 'Idempotency-Key': idempotencyKey },
    }
  );
  return res.data;
};
