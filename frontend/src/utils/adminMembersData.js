import { api } from './axios';

export const getAdminMembersData = async ({ keyword, role, status, generation } = {}) => {
  const params = {};

  if (keyword) params.keyword = keyword;
  if (role) params.role = role;
  if (status) params.status = status;
  if (generation) params.generation = generation;

  const response = await api.get('/api/admin/users', { params });
  return { members: response.data || [] };
};

export const changeAdminMemberRole = async ({ userId, role }) => {
  await api.patch(`/api/admin/users/${userId}/role`, null, {
    params: { role },
  });
};

export const changeAdminMemberStatus = async ({ userId, status }) => {
  await api.patch(`/api/admin/users/${userId}/status`, null, {
    params: { status },
  });
};

export const promoteAdminMemberSenior = async ({ userId }) => {
  await api.patch(`/api/admin/users/${userId}/senior`);
};

export const deleteAdminMember = async ({ userId }) => {
  await api.delete(`/api/admin/users/${userId}`);
};
