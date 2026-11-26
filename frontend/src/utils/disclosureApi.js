import { api } from './axios';

export const getDisclosureCompanies = async ({ q = '', page = 0, size = 200 } = {}) => {
  const params = { page, size };
  if (q && q.trim()) {
    params.q = q.trim();
  }
  const { data } = await api.get('/api/disclosures/companies', { params });
  return data;
};

export const getDisclosureCompanyDetail = async (corpCode, eventType = 'ALL') => {
  const params = {};
  if (eventType && eventType !== 'ALL') {
    params.type = eventType;
  }
  const { data } = await api.get(`/api/disclosures/companies/${corpCode}`, { params });
  return data;
};
