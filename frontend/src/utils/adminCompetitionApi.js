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
