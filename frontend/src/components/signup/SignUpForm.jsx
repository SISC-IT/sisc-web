import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';

import {
  sendVerificationNumber,
  signUp,
  checkVerificationNumber,
} from '../../utils/auth.js';

const SignUpForm = () => {
  const [nickname, setNickname] = useState('');
  const [verificationNumber, setVerificationNumber] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [isVerificationSent, setVerificationSent] = useState(false);
  const [isVerificationChecked, setVerificationChecked] = useState(false);

  const abortRef = useRef(null);

  const nav = useNavigate();

  // 이메일 유효성 검사
  const isEmailValid = () => {
    const emailRegex = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;
    return emailRegex.test(email);
  };

  // 핸드폰 번호 유효성 검사
  const isPhoneNumberValid = () => {
    const phoneRegex = /^0\d{8,10}$/;
    return phoneRegex.test(phoneNumber);
  };

  // 회원가입 제출 유효성 검사
  const isFormValid =
    nickname.trim() !== '' &&
    isEmailValid() &&
    isVerificationSent &&
    isVerificationChecked &&
    isPhoneNumberValid &&
    password.trim() !== '' &&
    password === confirmPassword;

  const handleSendVerificationNumber = async (e) => {
    e.preventDefault();

    // 도중에 요청 시 전 요청 취소
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    // 인증번호 발송 로직 & api 자리
    try {
      await sendVerificationNumber({ email: email }, abortRef.current.signal);

      setVerificationSent(true);
      alert('인증번호가 발송되었습니다.');
    } catch (err) {
      console.log('status:', err.status);
      console.log('data:', err.data);
      console.log('message:', err.message);
    }
  };
  const handleCheckVerificationNumber = async () => {
    // 도중에 요청 시 전 요청 취소
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    // 인증번호 발송 로직 & api 자리
    try {
      await checkVerificationNumber(
        { email: email, verificationNumber: verificationNumber },
        abortRef.current.signal
      );

      setVerificationChecked(true);
      alert('인증되었습니다.');
    } catch (err) {
      console.log('status:', err.status);
      console.log('data:', err.data);
      console.log('message:', err.message);
    }
  };

  const handleSignUp = async (e) => {
    e.preventDefault();

    // 도중에 요청 시 전 요청 취소
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    try {
      await signUp(
        {
          nickname,
          email,
          password,
          phoneNumber,
        },
        abortRef.current.signal
      );

      alert('회원가입이 완료되었습니다.');
      nav('/login');
    } catch (err) {
      console.log('status:', err.status);
      console.log('data:', err.data);
      console.log('message:', err.message);
    }
  };

  return (
    <>
      <div className={styles.formContainer}>
        <form className={styles.loginForm} onSubmit={handleSignUp}>
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
            <label htmlFor="nickname">닉네임</label>
            <input
              type="text"
              id="nickname"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="닉네임을 입력해주세요"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="email">Email</label>
            <div className={styles.phoneVerificationContainer}>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="ex) abcde@gmail.com"
                className={styles.phoneNumberInput}
              />
              <button
                type="button"
                className={styles.verifyButton}
                onClick={handleSendVerificationNumber}
                disabled={!isEmailValid()}
              >
                인증번호 발송
              </button>
            </div>
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="verificationNumber">인증번호</label>
            <div className={styles.phoneVerificationContainer}>
              <input
                type="text"
                id="verificationNumber"
                value={verificationNumber}
                onChange={(e) => setVerificationNumber(e.target.value)}
                placeholder="인증번호를 입력해주세요"
              />
              <button
                type="button"
                className={styles.verifyButton}
                onClick={handleCheckVerificationNumber}
                disabled={!isVerificationSent}
              >
                인증번호 확인
              </button>
            </div>
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="phoneNumber">전화번호</label>
            <input
              type="text"
              id="phoneNumber"
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
              placeholder="비밀번호를 입력해주세요"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="confirm-password">비밀번호 확인</label>
            <input
              type="password"
              id="confirm-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="비밀번호를 한번 더 입력해주세요"
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
    </>
  );
};

export default SignUpForm;
