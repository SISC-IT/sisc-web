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

  const handleLogin = (e) => {
    e.preventDefault();
    // 안전장치
    if (!email || !password) {
      alert('이메일과 비밀번호를 모두 입력해주세요.');
      return;
    }

    // 로그인 성공 시 로직
    localStorage.setItem('authToken', 'dummy-token-12345');
    nav('/');
  };

  return (
    <>
      <div className={styles.formContainer}>
        <form className={styles.loginForm} onSubmit={handleLogin}>
          <div className={styles.logoBox}>
            <img src={sejong_logo} alt="sejong_logo" className={styles.logo} />
          </div>

          <h1>Sejong Investment Scholars Club</h1>
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

        <SocialLoginButtons />
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
