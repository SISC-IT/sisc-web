import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';
import EmailVerificationModal from './../VerificationModal';

const SignUpForm = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isModalOpen, setModalOpen] = useState(false);
  const [confirmEmail, setConfirmEmail] = useState(false);
  const nav = useNavigate();

  // 이메일 입력 형태가 맞는지 검사
  const isEmailValid = () => {
    const emailRegex = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;
    return emailRegex.test(email);
  };

  // 핸드폰 번호 유효성 검사
  const isPhoneNumberValid = () => {
    const phoneRegex = /^\d{10,11}$/;
    return phoneRegex.test(phoneNumber);
  };

  // 회원가입 제출 유효성 검사
  const isFormValid =
    isEmailValid() &&
    isPhoneNumberValid() &&
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
  const openModal = () => {
    setModalOpen(true);
  };
  const closeModal = () => {
    setModalOpen(false);
  };

  const handleEmailVerified = () => {
    setConfirmEmail(true);
  };

  return (
    <>
      <div className={styles.formContainer}>
        <form className={styles.loginForm} onSubmit={handleSignUp}>
          <div className={styles.logoBox}>
            <img src={sejong_logo} alt="sejong_logo" className={styles.logo} />
          </div>
          <h1>Sejong Investment Scholars Club</h1>
          <div className={styles.inputGroup}>
            <label htmlFor="email">Email</label>
            <div className={styles.emailContainer}>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="이메일을 입력하세요"
                className={styles.emailInput}
              />
              <button
                type="button"
                onClick={openModal}
                className={styles.verifyButton}
                disabled={!isEmailValid()}
              >
                인증
              </button>
            </div>
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="phone-number">핸드폰 번호</label>
            <input
              type="text"
              id="phone-number"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
              placeholder="ex) 01012345678"
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
          <div className={styles.inputGroup}>
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
            className={styles.loginButton}
            disabled={!isFormValid}
          >
            회원가입
          </button>
        </form>
      </div>
      {isModalOpen && (
        <EmailVerificationModal
          onClose={closeModal}
          onEmailVerified={handleEmailVerified}
        />
      )}
    </>
  );
};

export default SignUpForm;
