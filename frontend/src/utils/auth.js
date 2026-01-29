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

  // 토큰은 자동으로 쿠키에 저장됨 (백엔드가 Set-Cookie 헤더로 전송)
  // res.data에는 유저 정보만 있음 (accessToken, refreshToken 제거됨)

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
