import { api } from './axios.js';

export const attendanceList = async () => {
  try {
    const res = await api.get('/api/attendance/me');
    // console.log('API BASE URL:', import.meta.env.VITE_API_URL);
    return res.data;
  } catch (err) {
    console.error('출석 세션 불러오기 중 오류 발생: ', err);
    throw err;
  }
};


// [
//   {
//     "attendanceId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
//     "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
//     "userName": "string",
//     "roundId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
//     "attendanceStatus": "string",
//     "checkedAt": "2026-02-24T14:33:43.581Z",
//     "note": "string",
//     "checkInLatitude": 0.1,
//     "checkInLongitude": 0.1,
//     "createdAt": "2026-02-24T14:33:43.581Z",
//     "updatedAt": "2026-02-24T14:33:43.581Z"
//   }
// ]
