import { api } from './axios';

const ensureUserId = (userId) => {
  const isNumber = typeof userId === 'number' && Number.isFinite(userId);
  const isNonEmptyString = typeof userId === 'string' && userId.trim() !== '';

  if (!isNumber && !isNonEmptyString) {
    throw new Error('userId is required');
  }
};

// 관리자 사용자 목록 조회
export const getAdminUsers = async ({ keyword, generation, role, status } = {}) => {
  const params = {};

  if (keyword) params.keyword = keyword;
  if (generation) params.generation = generation;
  if (role) params.role = role;
  if (status) params.status = status;

  const response = await api.get('/api/admin/users', { params });
  return response.data;
};

// 사용자 권한 변경
export const updateAdminUserRole = async ({ userId, role }) => {
  ensureUserId(userId);
  await api.patch(`/api/admin/users/${userId}/role`, null, {
    params: { role },
  });
};

// 사용자 상태 변경
export const updateAdminUserStatus = async ({ userId, status }) => {
  ensureUserId(userId);
  await api.patch(`/api/admin/users/${userId}/status`, null, {
    params: { status },
  });
};

// 사용자 신분(grade) 변경
export const updateAdminUserGrade = async ({ userId, grade }) => {
  ensureUserId(userId);
  await api.patch(`/api/admin/users/${userId}/grade`, null, {
    params: { grade },
  });
};

// 사용자 삭제
export const deleteAdminUser = async ({ userId }) => {
  ensureUserId(userId);
  await api.delete(`/api/admin/users/${userId}`);
};

// 관리자용 엑셀 업로드 API
export const uploadAdminUsersExcel = async ({ file }) => {
  const isValidFile = file instanceof File || file instanceof Blob;
  if (!isValidFile) {
    throw new Error('file is required');
  }

  const formData = new FormData();
  formData.append('file', file);

  const response = await api.post('/api/admin/users/upload-excel', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return response.data;
};
