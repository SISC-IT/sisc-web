import { api } from './axios.js';

const DEFAULT_ROLE = 'TEAM_MEMBER';

export const signUp = async (
  {
    studentName,
    studentId,
    email,
    password,
    phoneNumber,
    gender,
    college,
    department,
    generation,
    teamName,
    remark,
  },
  signal
) => {
  const payload = {
    studentName: studentName.trim(),
    studentId: studentId.trim(),
    email: email.trim(),
    password: password,
    phoneNumber: phoneNumber.trim(),
    gender: gender.trim(),
    college: college.trim(),
    department: department.trim(),
    generation: generation.trim(),
    teamName: teamName.trim(),
    remark: remark,
  };
  const res = await api.post('/api/user/signup', payload, { signal });
  return res.data;
};

export const login = async ({ studentId, password }, signal) => {
  const paylaod = { studentId, password };

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
export const resetPassword = async ({ email }, signal) => {
  const res = await api.post('/api/user/password/reset/send', null, {
    params: { email },
    signal,
  });

  return res.data;
};
