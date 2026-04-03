import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { toast } from 'react-toastify';
import { api } from '../utils/axios';

export const useAuthGuard = () => {
  const nav = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const checkAuth = async () => {
      try {
        // /api/user/details 호출하여 인증 상태 확인
        await api.get('/api/user/details');
        // 성공하면 아무것도 하지 않음
      } catch (error) {
        // 401 에러(인증 실패)만 로그인 페이지로 리다이렉트
        const isUnauthorized =
          error?.status === 401 || error?.response?.status === 401;

        if (isUnauthorized) {
          toast.error('로그인 후 이용하실 수 있습니다.');
          const returnUrl = encodeURIComponent(
            location.pathname + location.search
          );
          nav(`/login?returnUrl=${returnUrl}`);
        }
        // 404나 다른 에러는 무시
      }
    };

    checkAuth();
  }, [nav, location.pathname, location.search]);
};
