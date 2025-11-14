import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

const getParams = (search) => {
  const p = new URLSearchParams(search);
  return {
    accessToken: p.get('accessToken') || '',
    refreshToken: p.get('refreshToken') || '',
    userId: p.get('userId') || '',
    name: p.get('name') || '',
    next: p.get('next') || '/',
    error: p.get('error') || '',
  };
};

const OAuthSuccess = () => {
  const nav = useNavigate();
  const { search } = useLocation();
  const params = useMemo(() => getParams(search), [search]);

  const [status, setStatus] = useState('처리 중...');

  useEffect(() => {
    // 실패 케이스
    if (params.error) {
      setStatus(`로그인 실패: ${params.error}`);
      return;
    }

    // 토큰 저장
    if (params.accessToken) {
      localStorage.setItem('accessToken', params.accessToken);
    }
    if (params.refreshToken) {
      localStorage.setItem('refreshToken', params.refreshToken);
    }

    // (선택) 사용자 정보도 함께 보냈다면 저장해 두기
    if (params.userId) localStorage.setItem('userId', params.userId);
    if (params.name) localStorage.setItem('userName', params.name);

    setStatus('로그인 완료! 이동 중...');
    const to = decodeURIComponent(params.next || '/');

    // 살짝 지연 후 이동
    const t = setTimeout(() => nav(to, { replace: true }), 300);
    return () => clearTimeout(t);
  }, [params, nav]);

  return (
    <div style={{ minHeight: '60vh', display: 'grid', placeItems: 'center' }}>
      <div style={{ textAlign: 'center' }}>
        <h2>소셜 로그인</h2>
        <p>{status}</p>
        {/* 자동 이동이 안 될 때 수동 이동 버튼 */}
        {!params.error && (
          <button
            onClick={() =>
              nav(decodeURIComponent(params.next || '/'), { replace: true })
            }
            style={{
              marginTop: 12,
              padding: '10px 16px',
              borderRadius: 8,
              border: '1px solid #ddd',
              cursor: 'pointer',
            }}
          >
            계속하기
          </button>
        )}
        {params.error && (
          <button
            onClick={() => nav('/login', { replace: true })}
            style={{
              marginTop: 12,
              padding: '10px 16px',
              borderRadius: 8,
              border: '1px solid #ddd',
              cursor: 'pointer',
            }}
          >
            로그인 화면으로
          </button>
        )}
      </div>
    </div>
  );
};

export default OAuthSuccess;
