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

const me = (competitionId) => `/api/competitions/${competitionId}/me`;

// date를 생략하면 오늘(KST) 현황이다
export const getMySummary = async (competitionId) => {
  const res = await api.get(`${me(competitionId)}/summary`);
  return res.data;
};

export const getMyHoldings = async (competitionId) => {
  const res = await api.get(`${me(competitionId)}/holdings`);
  return res.data;
};

export const getMyTrades = async (competitionId, page) => {
  const res = await api.get(`${me(competitionId)}/trades`, {
    params: { page },
  });
  return res.data;
};

// type: DEPOSIT(현금 → 예금) | WITHDRAW(예금 → 현금)
export const transferDeposit = async (competitionId, type, amount) => {
  const res = await api.post(`${me(competitionId)}/deposit-transactions`, {
    type,
    amount,
  });
  return res.data;
};

export const getDepositTransactions = async (competitionId, page) => {
  const res = await api.get(`${me(competitionId)}/deposit-transactions`, {
    params: { page },
  });
  return res.data;
};

export const getRankings = async (competitionId) => {
  const res = await api.get(`/api/competitions/${competitionId}/rankings`);
  return res.data;
};

export const getTeamRankings = async (competitionId) => {
  const res = await api.get(
    `/api/competitions/${competitionId}/rankings/teams`
  );
  return res.data;
};
