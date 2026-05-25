import { api } from './axios';

const BASE_PATH = '/api/asset-management/accounts';

export const getAssetManagementAccountAccess = async () => {
  const { data } = await api.get(`${BASE_PATH}/access`);
  return data;
};

export const getAssetManagementAccounts = async () => {
  const { data } = await api.get(BASE_PATH);
  return data;
};

export const getAssetManagementDailyBalance = async (date) => {
  const { data } = await api.get(`${BASE_PATH}/daily-balance`, {
    params: date ? { date } : undefined,
  });
  return data;
};

export const getAssetManagementEvaluation = async (exchangeType = 'KRX') => {
  const { data } = await api.get(`${BASE_PATH}/evaluation`, {
    params: { exchangeType },
  });
  return data;
};
