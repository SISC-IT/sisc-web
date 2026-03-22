import { api } from './axios';

export const getUserDetails = async () => {
  const response = await api.get('/api/user/details');
  const data = response.data ?? {};

  return {
    id: data.id ?? null,
    name: data.name ?? '',
    email: data.email ?? '',
    phoneNumber: data.phoneNumber ?? null,
    point: Number.isFinite(data.point) ? data.point : 0,
    role: data.role ?? '',
    authorities: data.authorities ?? [],
  };
};

export const updateUserDetails = async ({
  email,
  currentPassword,
  newPassword,
}) => {
  const body = {
    email,
    currentPassword,
    newPassword,
  };

  const response = await api.patch('/api/user/details', body);

  return response.data;
};
