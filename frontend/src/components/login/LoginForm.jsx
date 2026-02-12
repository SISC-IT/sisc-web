import { useRef, useState } from 'react';
import { useNavigate, NavLink } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';

// import SocialLoginButtons from './SocialLoginButtons';
// import VerificationModal from './../VerificationModal';
import ResetPasswordModal from './ResetPasswordModal';
// import FindEmailResultModal from './FindEmailResultModal';

import { toast } from 'react-toastify';
import { useAuth } from '../../contexts/AuthContext';

const LoginForm = () => {
  const nav = useNavigate();

  const [studentId, setStudentId] = useState('');
  const [password, setPassword] = useState('');
  const [modalStep, setModalStep] = useState('closed');
  const { login: authLogin } = useAuth();

  const closeModal = () => {
    setModalStep('closed');
  };

  const isFormValid = studentId.trim().length === 8 && password.trim() !== '';

  const abortRef = useRef(null);

  const handleLogin = async (e) => {
    e.preventDefault();

    abortRef.current?.abort();
    abortRef.current = new AbortController();

    try {
      await authLogin(
        {
          studentId,
          password,
        },
        abortRef.current.signal
      );

      nav('/');
    } catch (err) {
      console.dir(err);
      toast.error('로그인에 실패하였습니다. 학번과 비밀번호를 확인해주세요.');
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
            <label htmlFor="studentId">학번</label>
            <input
              type="text"
              id="studentId"
              value={studentId}
              onChange={(e) => {
                const value = e.target.value.replace(/\D/g, '');
                if (value.length <= 8) setStudentId(value);
              }}
              placeholder="8자리 학번을 입력하세요"
              maxLength={8}
              inputMode="numeric"
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
              onClick={() => setModalStep('resetPassword')}
            >
              비밀번호 초기화
            </a>
          </div>
          <NavLink to="/signup" className={styles.text}>
            회원가입
          </NavLink>
        </div>

        {/* <SocialLoginButtons onSocialLogin={handleSocialLogin} /> */}
      </div>

      {modalStep === 'resetPassword' && (
        <ResetPasswordModal onClose={closeModal} />
      )}
    </>
  );
};

export default LoginForm;
