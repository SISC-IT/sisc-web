import { api } from './axios';

const ensureUserId = (userId) => {
  if (userId === undefined || userId === null || userId === '') {
    throw new Error('userId is required');
  }
};

// 관리자 회원 목록 조회 (검색/권한/상태/기수 필터)
export const getAdminMembersData = async ({ keyword, role, status, generation } = {}) => {
  const params = {};

  if (keyword) params.keyword = keyword;
  if (role) params.role = role;
  if (status) params.status = status;
  if (generation) params.generation = generation;

  const response = await api.get('/api/admin/users', { params });
  return { members: response.data || [] };
};

// 회원 권한 변경
export const changeAdminMemberRole = async ({ userId, role }) => {
  ensureUserId(userId);
  await api.patch(`/api/admin/users/${userId}/role`, null, {
    params: { role },
  });
};

// 회원 상태 변경
export const changeAdminMemberStatus = async ({ userId, status }) => {
  ensureUserId(userId);
  await api.patch(`/api/admin/users/${userId}/status`, null, {
    params: { status },
  });
};

// 회원 신분(grade) 변경
export const changeAdminMemberGrade = async ({ userId, grade }) => {
  ensureUserId(userId);
  await api.patch(`/api/admin/users/${userId}/grade`, null, {
    params: { grade },
  });
};

// 회원 강제 삭제
export const deleteAdminMember = async ({ userId }) => {
  ensureUserId(userId);
  await api.delete(`/api/admin/users/${userId}`);
};
