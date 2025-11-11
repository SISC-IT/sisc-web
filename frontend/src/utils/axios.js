import axios from 'axios';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
});

api.interceptors.response.use(
  (res) => res,
  async (err) => {
    // 토큰 로직
    const originRequest = err.config;

    // 액세스 토큰 만료 확인
    if (err.response?.status === 401 && !originRequest._retry) {
      originRequest._retry = true;

      try {
        const rt = localStorage.getItem('refreshToken');
        const res = await axios.post(
          `${import.meta.env.VITE_API_URL}/api/auth/reissue`,
          { refreshToken: rt },
          { withCredentials: true }
        );

        const newAccessToken = res.data.accessToken;

        localStorage.setItem('accessToken', newAccessToken);
        originRequest.headers.Authorization = `Bearer ${newAccessToken}`;

        return api(originRequest);
      } catch (refreshError) {
        console.error('Token refresh failed: ', refreshError);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');

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
  const at = localStorage.getItem('accessToken');
  if (at) config.headers.Authorization = `Bearer ${at}`;
  return config;
});
