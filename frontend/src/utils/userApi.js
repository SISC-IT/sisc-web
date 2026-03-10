import { api } from './axios';

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
