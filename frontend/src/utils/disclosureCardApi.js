import { api } from './axios';

export const getDisclosureCards = async ({
  category = 'ALL',
  q = '',
  sort = 'latest',
  page = 0,
  size = 12,
} = {}) => {
  const params = { sort, page, size };
  if (category && category !== 'ALL') {
    params.category = category;
  }
  if (q && q.trim()) {
    params.q = q.trim();
  }
  const { data } = await api.get('/api/disclosures/cards', { params });
  return data;
};

export const getDisclosureCardDetail = async (cardId) => {
  const { data } = await api.get(`/api/disclosures/cards/${cardId}`);
  return data;
};
