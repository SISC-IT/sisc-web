import { api } from './axios.js';

const DEFAULT_ROLE = 'TEAM_MEMBER';

export const signUp = async (
  { nickname, email, password, phoneNumber },
  signal
) => {
  const payload = {
    name: nickname.trim(),
    email: email.trim(),
    password: password,
    role: DEFAULT_ROLE,
    phoneNumber: phoneNumber.trim(),
  };
  const res = await api.post('/api/auth/signup', payload, { signal });
  return res.data;
};

export const login = async ({ email, password }, signal) => {
  const paylaod = { email, password };

  const res = await api.post('/api/auth/login', paylaod, { signal });

  const { accessToken, refreshToken } = res.data;
  if (accessToken && refreshToken) {
    // 3. 로컬 스토리지에 각 토큰을 저장
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
  }

  return res.data;
};

export const sendVerificationNumber = async ({ email }, signal) => {
  const res = await api.post('/api/email/send', null, {
    params: { email },
    signal,
  });

  return res.data;
};
export const checkVerificationNumber = async (
  { email, verificationNumber },
  signal
) => {
  const res = await api.post('/api/email/verify', null, {
    params: { email, code: verificationNumber },
    signal,
  });

  return res.data;
};
