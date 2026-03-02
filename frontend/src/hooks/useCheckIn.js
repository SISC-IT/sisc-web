import { useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { api } from '../utils/axios';

export const useCheckIn = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const processedTokenRef = useRef(null);

  useEffect(() => {
    if (!token) return; // token 없으면 실행 안 함
    if (processedTokenRef.current === token) return;
    processedTokenRef.current = token;

    const checkIn = async () => {
      try {
        await api.post('/api/attendance/check-in', {
          qrToken: token,
        });
        toast.success('출석이 완료되었습니다! ');

        // 새로고침 시 재요청 방지
        navigate(window.location.pathname, { replace: true });
      } catch (err) {
        // console.log('출석 실패:', err);

        toast.error(
          err?.message ||
            err?.data?.message ||
            err?.response?.data?.message ||
            '이미 출석했거나 만료된 QR입니다.'
        );
      }
    };

    checkIn();
  }, [token, navigate]);
};
