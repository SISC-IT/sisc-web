import axios from 'axios';

export const api = axios.create({
  baseURL: '',
  withCredentials: true,
});

api.interceptors.response.use(
  (res) => res,
  async (err) => {
    const originRequest = err.config;

    // 액세스 토큰 만료 확인
    if (err.response?.status === 401 && !originRequest._retry) {
      originRequest._retry = true;

      try {
        // refreshToken은 쿠키에서 자동으로 전송됨
        await axios.post(
          `${import.meta.env.VITE_API_URL}/api/auth/reissue`,
          {}, // body 비움
          { withCredentials: true }
        );

        // 새로운 accessToken은 쿠키에 자동 저장됨
        // localStorage 업데이트 불필요
        // Authorization 헤더 설정 불필요

        // 원래 요청 재시도
        return api(originRequest);
      } catch (refreshError) {
        console.error('Token refresh failed: ', refreshError);
        // localStorage에서 토큰 제거 불필요 (쿠키는 백엔드에서 관리)
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    const status = err.response?.status;
    const message =
      err.response?.data?.message ||
      err.response?.statusText ||
      '오류가 발생했습니다.';
    return Promise.reject({ status, message, data: err.response?.data });
  }
);

api.interceptors.request.use((config) => {
  // Authorization 헤더 설정 제거
  // 쿠키가 자동으로 전송됨 (withCredentials: true)
  return config;
});
