import { api } from './axios';

const toInt = (value, fallback = 0) => {
  const parsed = Number.parseInt(value, 10);
  return Number.isNaN(parsed) ? fallback : parsed;
};

export const getVisitorsTrend = async (days = 7) => {
  const response = await api.get('/api/admin/dashboard/stats/visitors/trend', {
    params: { days: toInt(days, 7) },
  });
  return response.data;
};

export const getVisitorsSummary = async () => {
  const response = await api.get('/api/admin/dashboard/stats/visitors/summary');
  return response.data;
};

export const getUsersDistribution = async () => {
  const response = await api.get('/api/admin/dashboard/stats/users/distribution');
  return response.data;
};

export const getBoardsSummary = async () => {
  const response = await api.get('/api/admin/dashboard/stats/boards/summary');
  return response.data;
};

export const getBoardsDistribution = async (days = 7) => {
  const response = await api.get('/api/admin/dashboard/stats/boards/distribution', {
    params: { days: toInt(days, 7) },
  });
  return response.data;
};

export const getDashboardActivities = async (page = 0, size = 15) => {
  const response = await api.get('/api/admin/dashboard/activities', {
    params: { page: toInt(page, 0), size: toInt(size, 15), sort: 'createdAt,desc' },
  });
  return response.data;
};

export const getDashboardActivitiesStreamUrl = () => '/api/admin/dashboard/activities/stream';
