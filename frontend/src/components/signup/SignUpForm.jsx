import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import loginStyles from '../login/LoginForm.module.css';
import signupStyles from './SignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';
import EmailVerificationPopup from './EmailVerificationPopup';

const SignUpForm = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isPopupOpen, setPopupOpen] = useState(false);
  const [confirmEmail, setConfirmEmail] = useState(false);
  const nav = useNavigate();

  // 이메일 입력 형태가 맞는지 검사
  const isEmailValid = () => {
    const emailRegex = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;
    return emailRegex.test(email);
  };

  // 회원가입 제출 유효성 검사
  const isFormValid =
    email.trim() !== '' &&
    password.trim() !== '' &&
    password === confirmPassword &&
    confirmEmail;

  const handleSignUp = (e) => {
    e.preventDefault();

    // api 자리

    // localStorage.setItem('authToken', 'dummy-token-12345');
    nav('/login'); // 회원가입 성공 시 로그인 페이지 이동
  };

  // 이메일 인증 팝업
  const openPopup = () => {
    setPopupOpen(true);
  };
  const closePopup = () => {
    setPopupOpen(false);
  };

  const handleEmailVerified = () => {
    setConfirmEmail(true);
  };

  return (
    <>
      <div className={loginStyles.formContainer}>
        <form className={loginStyles.loginForm} onSubmit={handleSignUp}>
          <div className={loginStyles.logoBox}>
            <img
              src={sejong_logo}
              alt="sejong_logo"
              className={loginStyles.logo}
            />
          </div>

          <h1>Sejong Investment Scholars Club</h1>
          <div className={loginStyles.inputGroup}>
            <label htmlFor="email">Email</label>
            <div className={signupStyles.emailContainer}>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="이메일을 입력하세요"
                className={signupStyles.emailInput}
              />
              <button
                type="button"
                onClick={openPopup}
                className={signupStyles.verifyButton}
                disabled={!isEmailValid()}
              >
                인증
              </button>
            </div>
          </div>
          <div className={loginStyles.inputGroup}>
            <label htmlFor="password">비밀번호</label>
            <input
              type="password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호를 입력하세요"
            />
          </div>
          <div className={loginStyles.inputGroup}>
            <label htmlFor="confirm-password">비밀번호 확인</label>
            <input
              type="password"
              id="confirm-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="비밀번호를 한번 더 입력하세요"
            />
          </div>
          <button
            type="submit"
            className={loginStyles.loginButton}
            disabled={!isFormValid}
          >
            회원가입
          </button>
        </form>
      </div>
      {isPopupOpen && (
        <EmailVerificationPopup
          onClose={closePopup}
          onEmailVerified={handleEmailVerified}
        />
      )}
    </>
  );
};

export default SignUpForm;
