import { api } from './axios.js';

// 라운드 QR SSE 연결
export const connectRoundQrStream = (roundId, onMessage, onError) => {
  const baseURL = api.defaults.baseURL;

  const url = `${baseURL}/api/attendance/rounds/${roundId}/qr-stream`;

  const eventSource = new EventSource(url, {
    withCredentials: true,
  });

  eventSource.onopen = () => {
    console.log('SSE 연결 성공');
  };

  // 중요
  eventSource.addEventListener('qrToken', (event) => {
    const parsed = JSON.parse(event.data);
    onMessage?.(parsed);
  });

  eventSource.onerror = (err) => {
    console.error('QR SSE 연결 오류', err);
    onError?.(err);
    eventSource.close();
  };

  return eventSource;
};

// QR 체크인 요청
export const checkInWithQr = async (token) => {
  try {
    const res = await api.post('/api/attendance/check-in', {
      token,
    });

    return res.data;
  } catch (err) {
    console.error('QR 출석 체크 중 오류 발생', err);
    throw err;
  }
};
