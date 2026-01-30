import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { api } from '../utils/axios';

export const useAuthGuard = () => {
  const nav = useNavigate();

  useEffect(() => {
    const checkAuth = async () => {
      try {
        // /api/user/details 호출하여 인증 상태 확인
        await api.get('/api/user/details');
        // 성공하면 아무것도 하지 않음
      } catch (error) {
        // 401 에러만 로그인 페이지로 리다이렉트
        if (error.status === 401) {
          toast.error('로그인 후 이용하실 수 있습니다.');
          nav('/login');
        }
        // 404나 다른 에러는 무시
      }
    };

    checkAuth();
  }, [nav]);
};
