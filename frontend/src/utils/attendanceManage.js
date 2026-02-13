import { api } from './axios.js';

// 현재 시간 JSON 포맷
// const getFormattedCurrentTime = () => {
//   const now = new Date();

//   const year = now.getFullYear();
//   const month = String(now.getMonth() + 1).padStart(2, '0');
//   const day = String(now.getDate()).padStart(2, '0');
//   const hours = String(now.getHours()).padStart(2, '0');
//   const minutes = String(now.getMinutes()).padStart(2, '0');
//   const seconds = String(now.getSeconds()).padStart(2, '0');

//   return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
// };

// 모든 세션 조회
export const getAttendanceSessions = async () => {
  try {
    const res = await api.get('/api/attendance/sessions');
    // console.log('API BASE URL:', import.meta.env.VITE_API_URL);
    console.log(res.data);
    return res.data;
  } catch (err) {
    console.error('출석 세션 불러오기 중 오류 발생: ', err);
    throw err;
  }
};

// 세션 생성 및 추가
export const createAttendanceSession = async (sessionData) => {
  try {
    const res = await api.post('/api/attendance/sessions', sessionData);
    return res.data;
  } catch (err) {
    console.error('출석 세션 생성 중 오류 발생: ', err);
    throw err;
  }
};

// 특정 세션 삭제
export const deleteSession = async (sessionId) => {
  try {
    const res = await api.delete(`/api/attendance/sessions/${sessionId}`);
    return res.data;
  } catch (err) {
    console.error('세션 삭제 중 오류 발생', err);
    throw err;
  }
};

// 세션 아이디로 모든 회차 조회
export const getRounds = async (sessionId) => {
  try {
    const res = await api.get(`/api/attendance/sessions/${sessionId}/rounds`);
    return res.data;
  } catch (err) {
    console.error('회차 조회 중 오류 발생', err);
    throw err;
  }
};

// 회차 추가
export const addRound = async (sessionId, newRound) => {
  const paylaod = {
    sessionId,
    roundDate: newRound.roundDate,
    startTime: newRound.startTime,
    allowedMinutes: newRound.availableMinutes,
  };

  try {
    const res = await api.post(
      `/api/attendance/sessions/${sessionId}/rounds`,
      paylaod
    );
    return res.data;
  } catch (err) {
    console.error('회차 추가 중 오류 발생', err);
    throw err;
  }
};

// 회차 삭제
export const deleteRound = async (roundId) => {
  try {
    const res = await api.delete(`/api/attendance/rounds/${roundId}`);
    return res.data;
  } catch (err) {
    console.error('회차 삭제 중 오류 발생', err);
    throw err;
  }
};

// 세션 정보 수정
export const changeSessionData = async (updateSessionData) => {
  try {
    const res = await api.put(
      `/api/attendance/sessions/${updateSessionData.attendanceSessionId}`,
      updateSessionData
    );
    return res.data;
  } catch (err) {
    console.error('세션 정보 수정 중 오류 발생', err);
    throw err;
  }
};

// 회차 정보 수정
export const changeRoundData = async (roundId, updateRoundData) => {
  try {
    const res = await api.put(
      `/api/attendance/rounds/${roundId}`,
      updateRoundData
    );
    return res.data;
  } catch (err) {
    console.error('회차 정보 수정 중 오류 발생', err);
    throw err;
  }
};

// 세션에 유저 추가
export const addUser = async (sessionId, userId) => {
  try {
    const res = await api.post(`/api/attendance/sessions/${sessionId}/users`, {
      userId: userId,
    });
    return res.data;
  } catch (err) {
    console.error('유저 추가 중 오류 발생', err);
    throw err;
  }
};

// 세션 참여자 조회
export const getUsers = async (sessionId) => {
  try {
    const res = await api.get(`/api/attendance/sessions/${sessionId}/users`);
    return res.data;
  } catch (err) {
    console.error('유저 조회 중 오류 발생', err);
    throw err;
  }
};

// 세션 출석 기록 조회
export const getAttendance = async () => {
  try {
    const res = await api.get(`/api/attendance/history`);
    return res.data;
  } catch (err) {
    console.error('출석 조회 중 오류 발생', err);
    throw err;
  }
};

// 특정 세션 출석 기록 조회
export const getSessionAttendance = async (sessionId) => {
  try {
    const res = await api.get(
      `/api/attendance/sessions/${sessionId}/attendances`
    );
    return res.data;
  } catch (err) {
    console.error('특정 세션의 출석 조회 중 오류 발생', err);
    throw err;
  }
};

// 출석 상태 수정
export const changeUserAttendance = async (roundId, userId, statusDetails) => {
  try {
    const res = await api.put(
      `/api/attendance/rounds/${roundId}/attendances/${userId}`,
      statusDetails
    );
    return res.data;
  } catch (err) {
    console.error('출석 상태 수정 중 오류 발생', err);
    throw err;
  }
};

// 라운드 별 출석 조회
export const getRoundUserAttendance = async (roundId) => {
  try {
    const res = await api.get(`/api/attendance/rounds/${roundId}/attendances`);
    return res.data;
  } catch (err) {
    console.error('라운드별 출석 조회 중 오류 발생', err);
    throw err;
  }
};

// 전체 유저 조회
export const getUserList = async () => {
  try {
    const res = await api.get('/api/attendance/sessions/get-users');
    return res.data;
  } catch (err) {
    console.error('모든 유저 데이터 조회 중 오류 발생', err);
    throw err;
  }
};
