import { api } from './axios';

// 가입 승인 대기 회원 목록 조회
export const getAdminMemberManageData = async ({ keyword } = {}) => {
  const params = {
    role: 'PENDING_MEMBER',
  };

  if (keyword) params.keyword = keyword;

  const response = await api.get('/api/admin/users', { params });

  return {
    pendingMembers: response.data || [],
    monthlyApprovedCount: 0,
    monthlyRejectedCount: 0,
  };
};

// 단일 회원 가입 승인 (대기 -> 일반 회원)
export const approvePendingMember = async ({ userId }) => {
  await api.patch(`/api/admin/users/${userId}/role`, null, {
    params: { role: 'TEAM_MEMBER' },
  });
};

// 단일 회원 가입 거절 (상태 -> OUT)
export const rejectPendingMember = async ({ userId }) => {
  await api.patch(`/api/admin/users/${userId}/status`, null, {
    params: { status: 'OUT' },
  });
};

// 다중 회원 일괄 가입 승인
export const approvePendingMembersBulk = async ({ userIds }) => {
  await Promise.all(
    userIds.map((userId) =>
      api.patch(`/api/admin/users/${userId}/role`, null, {
        params: { role: 'TEAM_MEMBER' },
      })
    )
  );
};

// 다중 회원 일괄 가입 거절
export const rejectPendingMembersBulk = async ({ userIds }) => {
  await Promise.all(
    userIds.map((userId) =>
      api.patch(`/api/admin/users/${userId}/status`, null, {
        params: { status: 'OUT' },
      })
    )
  );
};
