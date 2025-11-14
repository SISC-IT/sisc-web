import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

const getParams = (search) => {
  const p = new URLSearchParams(search);

  // next 파라미터 검증 (내부 경로만 허용)
  const nextParam = p.get('next') || '/';
  const isValidNext = nextParam.startsWith('/') && !nextParam.startsWith('//');

  // 에러 메시지 sanitize
  const errorParam = p.get('error') || '';
  const sanitizedError = errorParam.replace(/[<>]/g, '');

  return {
    accessToken: p.get('accessToken') || '',
    refreshToken: p.get('refreshToken') || '',
    userId: p.get('userId') || '',
    name: p.get('name') || '',
    next: isValidNext ? nextParam : '/',
    error: sanitizedError,
  };
};

const ERROR_MESSAGES = {
  invalid_credentials: '잘못된 인증 정보입니다.',
  server_error: '서버 오류가 발생했습니다.',
  cancelled: '로그인이 취소되었습니다.',
  access_denied: '접근이 거부되었습니다.',
};

const getSafeRedirectPath = (encodedPath) => {
  try {
    const decoded = decodeURIComponent(encodedPath || '/');
    // 내부 경로만 허용 (프로토콜이나 도메인 없음)
    if (decoded.startsWith('/') && !decoded.startsWith('//')) {
      return decoded;
    }
  } catch (e) {
    console.error('Invalid redirect path:', e);
  }
  return '/';
};

const OAuthSuccess = () => {
  const nav = useNavigate();
  const { search } = useLocation();
  const params = useMemo(() => getParams(search), [search]);
  const safePath = useMemo(
    () => getSafeRedirectPath(params.next),
    [params.next]
  );

  const [status, setStatus] = useState('처리 중...');

  useEffect(() => {
    // 실패 케이스
    if (params.error) {
      const errorMsg =
        ERROR_MESSAGES[params.error] || '알 수 없는 오류가 발생했습니다.';
      setStatus(`로그인 실패: ${errorMsg}`);
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

    // 살짝 지연 후 이동
    const t = setTimeout(() => nav(safePath, { replace: true }), 300);
    return () => clearTimeout(t);
  }, [params, nav, safePath]);

  return (
    <div style={{ minHeight: '60vh', display: 'grid', placeItems: 'center' }}>
      <div style={{ textAlign: 'center' }}>
        <h2>소셜 로그인</h2>
        <p>{status}</p>
        {/* 자동 이동이 안 될 때 수동 이동 버튼 */}
        {!params.error && (
          <button
            onClick={() => nav(safePath, { replace: true })}
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
