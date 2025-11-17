import { useRef, useState } from 'react';
import { useNavigate, NavLink } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';

import SocialLoginButtons from './SocialLoginButtons';
import VerificationModal from './../VerificationModal';
import ResetPasswordModal from './ResetPasswordModal';
import FindEmailResultModal from './FindEmailResultModal';

import { login } from '../../utils/auth';

import { api } from './../../utils/axios.js';
const getUserDetails = async () => {
  try {
    const res = await api.get('/api/user/details');
    return res.data;
  } catch (err) {
    console.error('[getUserDetails] error:', err);
    throw err;
  }
};

const LoginForm = () => {
  const nav = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [modalStep, setModalStep] = useState('closed');
  const [foundEmail, setFoundEmail] = useState('');

  // 전화번호 인증 성공 시 호출하는 함수
  const handlePhoneVerificationSuccess = () => {
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

  const abortRef = useRef(null);

  const handleLogin = async (e) => {
    e.preventDefault();

    // 도중에 요청 시 전 요청 취소
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    try {
      await login(
        {
          email,
          password,
        },
        abortRef.current.signal
      );

      nav('/');
    } catch (err) {
      console.dir(err);
      alert(
        err.data?.errorMessage ||
          '로그인에 실패하였습니다. 이메일과 비밀번호를 확인해주세요.'
      );
    }
  };

  const handleSocialLogin = (provider) => {
    // console.log(
    //   `${import.meta.env.VITE_API_URL}/oauth2/authorization/${provider}`
    // );
    window.location.href = `${import.meta.env.VITE_API_URL}/oauth2/authorization/${provider}`;
  };

  return (
    <>
      <div className={styles.formContainer}>
        <button
          onClick={async () => {
            try {
              const user = await getUserDetails();
              console.log('USER:', user);
            } catch (e) {
              console.error(e);
            }
          }}
        >
          유저 정보 확인
        </button>
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
              autoComplete="email"
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
              autoComplete="current-password"
            />
          </div>
          <button
            type="submit"
            className={styles.loginButton}
            disabled={!isFormValid}
          >
            로그인
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

        <SocialLoginButtons onSocialLogin={handleSocialLogin} />
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
