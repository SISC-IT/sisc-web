import { api } from './axios';

export const getPublicPage = async (pageType) => {
  if (!pageType) throw new Error('pageType is required');
  const response = await api.get(`/api/admin/public-pages/${pageType}`);
  return response.data;
};

export const savePublicPage = async (pageType, payload) => {
  if (!pageType) throw new Error('pageType is required');
  const response = await api.put(`/api/admin/public-pages/${pageType}`, payload);
  return response.data;
};

export const updatePublicPost = async (postId, payload) => {
  if (!postId) throw new Error('postId is required');
  const response = await api.patch(`/api/admin/public/posts/${postId}`, payload);
  return response.data;
};
