import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../utils/axios';

const OAuthSuccess = () => {
  const nav = useNavigate();
  const [status, setStatus] = useState('처리 중...');

  useEffect(() => {
    const fetchUserInfo = async () => {
      try {
        // 쿠키에 토큰이 저장되어 있으므로 바로 API 호출
        const { data } = await api.get('/api/user/details');
        
        // 선택사항: 유저 정보를 localStorage에 저장
        localStorage.setItem('user', JSON.stringify({
          userId: data.userId,
          name: data.name,
          email: data.email,
          phoneNumber: data.phoneNumber,
          point: data.point,
          role: data.role
        }));
        
        setStatus('로그인 완료! 이동 중...');
        setTimeout(() => nav('/', { replace: true }), 500);
        
      } catch (error) {
        console.error('유저 정보 조회 실패:', error);
        setStatus('로그인 실패: 유저 정보를 가져올 수 없습니다.');
        setTimeout(() => nav('/login', { replace: true }), 2000);
      }
    };
    
    fetchUserInfo();
  }, [nav]);

  return (
    <div style={{ 
      minHeight: '100vh', 
      display: 'flex', 
      alignItems: 'center', 
      justifyContent: 'center',
      background: 'linear-gradient(to bottom, #000000, #060d2a)'
    }}>
      <div style={{ textAlign: 'center', color: '#ffffff' }}>
        <h2 style={{ fontSize: '24px', marginBottom: '16px' }}>소셜 로그인</h2>
        <p style={{ fontSize: '16px' }}>{status}</p>
        <div style={{ 
          marginTop: '20px',
          width: '40px',
          height: '40px',
          border: '3px solid rgba(255,255,255,0.3)',
          borderTop: '3px solid #ffffff',
          borderRadius: '50%',
          margin: '20px auto',
          animation: 'spin 1s linear infinite'
        }} />
        <style>{`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    </div>
  );
};

export default OAuthSuccess;
