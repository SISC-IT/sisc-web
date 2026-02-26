import { api } from './axios';

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
  await api.patch(`/api/admin/users/${userId}/role`, null, {
    params: { role },
  });
};

// 회원 상태 변경
export const changeAdminMemberStatus = async ({ userId, status }) => {
  await api.patch(`/api/admin/users/${userId}/status`, null, {
    params: { status },
  });
};

// 회원을 선배(SENIOR)로 전환
export const promoteAdminMemberSenior = async ({ userId }) => {
  await api.patch(`/api/admin/users/${userId}/senior`);
};

// 회원 강제 삭제
export const deleteAdminMember = async ({ userId }) => {
  await api.delete(`/api/admin/users/${userId}`);
};
