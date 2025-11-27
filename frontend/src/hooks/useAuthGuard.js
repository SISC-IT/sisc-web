import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export const useAuthGuard = () => {
  const nav = useNavigate();

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      alert('로그인 후 이용하실 수 있습니다.');
      nav('/login');
    }
  }, [nav]);
};
