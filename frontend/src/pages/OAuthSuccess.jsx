import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

// 쿠키에서 특정 값 읽기
const getCookie = (name) => {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return parts.pop().split(';').shift();
  return null;
};

const OAuthSuccess = () => {
  const nav = useNavigate();
  const [status, setStatus] = useState('처리 중...');

  useEffect(() => {
    // 쿠키에서 토큰 읽기
    const accessToken = getCookie('access');
    const refreshToken = getCookie('refresh');

    if (accessToken && refreshToken) {
      // localStorage에 저장
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      
      setStatus('로그인 완료! 이동 중...');
      
      // 홈으로 이동
      setTimeout(() => {
        nav('/', { replace: true });
      }, 500);
    } else {
      setStatus('로그인 실패: 토큰을 받지 못했습니다.');
      setTimeout(() => {
        nav('/login', { replace: true });
      }, 2000);
    }
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
