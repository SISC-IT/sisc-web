import { api } from './axios';

export const getAdminUsers = async ({ keyword, generation, role, status } = {}) => {
  const params = {};

  if (keyword) params.keyword = keyword;
  if (generation) params.generation = generation;
  if (role) params.role = role;
  if (status) params.status = status;

  const response = await api.get('/api/admin/users', { params });
  return response.data;
};

export const updateAdminUserRole = async ({ userId, role }) => {
  await api.patch(`/api/admin/users/${userId}/role`, null, {
    params: { role },
  });
};

export const updateAdminUserStatus = async ({ userId, status }) => {
  await api.patch(`/api/admin/users/${userId}/status`, null, {
    params: { status },
  });
};

export const promoteAdminUserSenior = async ({ userId }) => {
  await api.patch(`/api/admin/users/${userId}/senior`);
};

export const deleteAdminUser = async ({ userId }) => {
  await api.delete(`/api/admin/users/${userId}`);
};

export const uploadAdminUsersExcel = async ({ file }) => {
  const formData = new FormData();
  formData.append('file', file);

  const response = await api.post('/api/admin/users/upload-excel', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return response.data;
};
