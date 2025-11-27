import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';

export const useAuthGuard = () => {
  const nav = useNavigate();

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      toast.error('로그인 후 이용하실 수 있습니다.');
      nav('/login');
    }
  }, [nav]);
};
