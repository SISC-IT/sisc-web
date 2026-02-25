import { api } from './axios';

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

export const approvePendingMember = async ({ userId }) => {
  await api.patch(`/api/admin/users/${userId}/role`, null, {
    params: { role: 'TEAM_MEMBER' },
  });
};

export const rejectPendingMember = async ({ userId }) => {
  await api.patch(`/api/admin/users/${userId}/status`, null, {
    params: { status: 'OUT' },
  });
};

export const approvePendingMembersBulk = async ({ userIds }) => {
  await Promise.all(
    userIds.map((userId) =>
      api.patch(`/api/admin/users/${userId}/role`, null, {
        params: { role: 'TEAM_MEMBER' },
      })
    )
  );
};

export const rejectPendingMembersBulk = async ({ userIds }) => {
  await Promise.all(
    userIds.map((userId) =>
      api.patch(`/api/admin/users/${userId}/status`, null, {
        params: { status: 'OUT' },
      })
    )
  );
};
