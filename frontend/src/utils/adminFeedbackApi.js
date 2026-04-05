import { api } from './axios';

const toInt = (value, fallback = 0) => {
  const parsed = Number.parseInt(value, 10);
  return Number.isNaN(parsed) ? fallback : parsed;
};

export const getAdminFeedbacks = async ({ page = 0, size = 10 } = {}) => {
  const response = await api.get('/api/admin/feedbacks', {
    params: {
      page: toInt(page, 0),
      size: toInt(size, 10),
      sort: 'createdDate,desc',
    },
  });

  return response.data;
};
