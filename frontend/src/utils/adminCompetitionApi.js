import { api } from './axios.js';

export const createCompetition = async ({ name, startDate, endDate }) => {
  const res = await api.post('/api/admin/competitions', {
    name,
    startDate,
    endDate,
  });
  return res.data;
};

export const finalizeCompetition = async (competitionId) => {
  await api.post(`/api/admin/competitions/${competitionId}/finalize`);
};

export const getAdminRankings = async (competitionId) => {
  const res = await api.get(
    `/api/admin/competitions/${competitionId}/rankings`
  );
  return res.data;
};

export const getAdminTeamRankings = async (competitionId) => {
  const res = await api.get(
    `/api/admin/competitions/${competitionId}/rankings/teams`
  );
  return res.data;
};
