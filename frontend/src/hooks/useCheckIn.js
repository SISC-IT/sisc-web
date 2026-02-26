import { useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { api } from '../utils/axios';

export const useCheckIn = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const hasChecked = useRef(false); // 중복 실행 방지

  useEffect(() => {
    console.log('=== useCheckIn 실행 ===');
    console.log('현재 token:', token);

    if (!token) return; // token 없으면 실행 안 함
    if (hasChecked.current) return; // 이미 실행했으면 다시 안 함

    hasChecked.current = true;

    const checkIn = async () => {
      try {
        console.log('📡 출석 API 호출 시작');

        await api.post('/api/attendance/check-in', {
          qrToken: token,
        });
        console.log('출석 성공');
        toast.success('출석이 완료되었습니다! ');

        // 새로고침 시 재요청 방지
        navigate(window.location.pathname, { replace: true });
      } catch (err) {
        console.log('출석 실패:', err);

        toast.error(
          err.response?.data?.message || '이미 출석했거나 만료된 QR입니다.'
        );
      }
    };

    checkIn();
  }, [token, navigate]);
};
