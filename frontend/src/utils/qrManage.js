import { api } from './axios.js';

// 라운드 QR SSE 연결
export const connectRoundQrStream = (roundId, onMessage, onError) => {
  const eventSource = new EventSource(
    `/api/attendance/rounds/${roundId}/qr-stream`,
    { withCredentials: true } // 쿠키 인증 사용하는 경우
  );

  eventSource.onmessage = (event) => {
    if (onMessage) {
      onMessage(event.data); // qrToken
    }
  };

  eventSource.onerror = (err) => {
    console.error('QR SSE 연결 오류', err);
    if (onError) {
      onError(err);
    }
    eventSource.close();
  };

  return eventSource; // 필요 시 외부에서 close 가능
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
