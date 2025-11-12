import { useState } from 'react';
import { useNavigate, NavLink } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';

import SocialLoginButtons from './SocialLoginButtons';
import VerificationModal from './../VerificationModal';
import ResetPasswordModal from './ResetPasswordModal';
import FindEmailResultModal from './FindEmailResultModal';

const LoginForm = () => {
  const nav = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [modalStep, setModalStep] = useState('closed');
  const [foundEmail, setFoundEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  // 전화번호 인증 성공 시 호출하는 함수
  const handlePhoneVerificationSuccess = (result) => {
    if (modalStep === 'verifyPhoneForEmail') {
      setFoundEmail('example@google.com');
      setModalStep('showEmail');
    } else if (modalStep === 'verifyPhoneForPassword') {
      setModalStep('resetPassword');
    }
  };

  const closeModal = () => {
    setModalStep('closed');
  };

  const isFormValid = email.trim() !== '' && password.trim() !== '';

  const handleLogin = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setLoading(true);

    // 안전장치
    if (!email || !password) {
      alert('이메일과 비밀번호를 모두 입력해주세요.');
      setLoading(false);
      return;
    }

    try {
      console.log('📋 로그인 시작:', email);

      const loginData = {
        email: email,
        password: password,
      };

      console.log('🔄 로그인 API 호출 중...');
      const response = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(loginData),
      });

      const data = await response.json();
      console.log('📨 백엔드 응답:', response.status, data);

      if (response.ok) {
        console.log('✅ 로그인 성공:', data);
        // 토큰과 사용자 정보 저장
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        localStorage.setItem('userNickname', data.name || email.split('@')[0]);

        console.log('✅ 로그인 완료:', {
          email: email,
          nickname: data.name,
          timestamp: new Date().toLocaleString('ko-KR'),
        });
        nav('/');
      } else {
        // 백엔드 에러 메시지 처리
        const errorMsg = data.message || '로그인에 실패했습니다.';
        console.error('❌ 로그인 실패:', errorMsg);
        setErrorMessage(errorMsg);
      }
    } catch (err) {
      console.error('❌ 로그인 API 오류:', err.message);
      setErrorMessage('서버 연결 오류가 발생했습니다. 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  };

  // OAuth 로그인 핸들러들
  const handleGoogleLogin = async () => {
    try {
      console.log('🔵 Google OAuth 로그인 시작');
      const response = await fetch('http://localhost:8080/api/auth/oauth/GOOGLE/init', {
        credentials: 'include',
      });
      const authUrl = await response.text();
      console.log('🔗 Google 인증 URL:', authUrl);
      window.location.href = authUrl;
    } catch (err) {
      console.error('❌ Google OAuth 초기화 실패:', err);
      setErrorMessage('Google 로그인 초기화에 실패했습니다.');
    }
  };

  const handleGithubLogin = async () => {
    try {
      console.log('🐙 Github OAuth 로그인 시작');
      const response = await fetch('http://localhost:8080/api/auth/oauth/GITHUB/init', {
        credentials: 'include',
      });
      const authUrl = await response.text();
      console.log('🔗 Github 인증 URL:', authUrl);
      window.location.href = authUrl;
    } catch (err) {
      console.error('❌ Github OAuth 초기화 실패:', err);
      setErrorMessage('Github 로그인 초기화에 실패했습니다.');
    }
  };

  const handleKakaoLogin = async () => {
    try {
      console.log('💛 Kakao OAuth 로그인 시작');
      const response = await fetch('http://localhost:8080/api/auth/oauth/KAKAO/init', {
        credentials: 'include',
      });
      const authUrl = await response.text();
      console.log('🔗 Kakao 인증 URL:', authUrl);
      window.location.href = authUrl;
    } catch (err) {
      console.error('❌ Kakao OAuth 초기화 실패:', err);
      setErrorMessage('Kakao 로그인 초기화에 실패했습니다.');
    }
  };

  return (
    <>
      <div className={styles.formContainer}>
        <form className={styles.loginForm} onSubmit={handleLogin}>
          <div className={styles.header}>
            <div className={styles.logoBox}>
              <img
                src={sejong_logo}
                alt="sejong_logo"
                className={styles.logo}
              />
            </div>

            <h1>Sejong Investment Scholars Club</h1>
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="email">Email</label>
            <input
              type="email"
              id="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="이메일을 입력하세요"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="password">비밀번호</label>
            <input
              type="password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력하세요"
            />
          </div>
          {errorMessage && (
            <div style={{
              padding: '10px',
              marginBottom: '15px',
              backgroundColor: '#ffebee',
              border: '1px solid #ef5350',
              borderRadius: '4px',
              color: '#c62828',
              fontSize: '14px'
            }}>
              {errorMessage}
            </div>
          )}
          <button
            type="submit"
            className={styles.loginButton}
            disabled={!isFormValid || loading}
          >
            {loading ? '로그인 중...' : '로그인'}
          </button>
        </form>
        <div className={styles.textContainer}>
          <div>
            <a
              className={styles.text}
              onClick={() => setModalStep('verifyPhoneForEmail')}
            >
              이메일 찾기
            </a>
            <span className={styles.divider} aria-hidden="true">
              |
            </span>
            <a
              className={styles.text}
              onClick={() => setModalStep('verifyPhoneForPassword')}
            >
              비밀번호 찾기
            </a>
          </div>
          <NavLink to="/signup" className={styles.text}>
            회원가입
          </NavLink>
        </div>

        <SocialLoginButtons
          onGoogle={handleGoogleLogin}
          onGithub={handleGithubLogin}
          onKakao={handleKakaoLogin}
        />
      </div>

      {(modalStep === 'verifyPhoneForEmail' ||
        modalStep === 'verifyPhoneForPassword') && (
        <VerificationModal
          title={
            modalStep === 'verifyPhoneForEmail'
              ? '이메일 찾기'
              : '비밀번호 찾기'
          }
          onClose={closeModal}
          onSuccess={handlePhoneVerificationSuccess}
        />
      )}

      {modalStep === 'showEmail' && (
        <FindEmailResultModal
          title="이메일 찾기 결과"
          onClose={closeModal}
          result={foundEmail}
        />
      )}

      {modalStep === 'resetPassword' && (
        <ResetPasswordModal onClose={closeModal} />
      )}
    </>
  );
};

export default LoginForm;
