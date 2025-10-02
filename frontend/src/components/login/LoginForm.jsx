import { useState } from 'react';
import { useNavigate, NavLink } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';

import SocialLoginButtons from './SocialLoginButtons';
import VerificationModal from './../VerificationModal';

const LoginForm = () => {
  const nav = useNavigate();

  const [id, setId] = useState('');
  const [password, setPassword] = useState('');
  // const [isModalOpen, setModalOpen] = useState(false);
  const [isFindEmailModalOpen, setFindEmailModalOpen] = useState(false);
  const [isFindPasswordModalOpen, setFindPasswordModalOpen] = useState(false);

  // 모달 띄우기 & 끄기
  const openModal = (idx) => {
    switch (idx) {
      case 1:
        setFindEmailModalOpen(true);
        break;
      case 2:
        setFindPasswordModalOpen(true);
        break;
    }
    // setModalOpen(true);
  };
  const closeModal = (idx) => {
    switch (idx) {
      case 1:
        setFindEmailModalOpen(false);
        break;
      case 2:
        setFindPasswordModalOpen(false);
        break;
    }
    // setModalOpen(false);
  };

  // 각 모드에 맞는 함수들
  const findEmail = () => {};
  const findPassword = () => {};

  const isFormValid = id.trim() !== '' && password.trim() !== '';

  const handleLogin = (e) => {
    e.preventDefault();

    // 안전장치
    if (!id || !password) {
      alert('아이디와 비밀번호를 모두 입력해주세요.');
      return;
    }

    // 로그인 성공&실패 로직

    // 로컬 스토리지
    localStorage.setItem('authToken', 'dummy-token-12345');
    nav('/'); // 임시 페이지 이동
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
              value={id}
              onChange={(e) => setId(e.target.value)}
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
            <NavLink
              className={styles.text}
              onClick={() => {
                openModal(1);
              }}
              // mode={findEmail}
            >
              이메일 찾기
            </NavLink>
            <span className={styles.divider} aria-hidden="true">
              |
            </span>
            <NavLink
              className={styles.text}
              onClick={() => {
                openModal(2);
              }}
              // mode={findPassword}
            >
              비밀번호 찾기
            </NavLink>
          </div>
          <NavLink to="/signup" className={styles.text}>
            회원가입
          </NavLink>
        </div>

        {/* 소셜 로그인 버튼들 */}
        <SocialLoginButtons />
      </div>
      {isFindEmailModalOpen && (
        <VerificationModal
          onClose={() => {
            closeModal(1);
          }}
          title={'이메일 찾기'}
        />
      )}
      {isFindPasswordModalOpen && (
        <VerificationModal
          onClose={() => {
            closeModal(2);
          }}
          title={'비밀번호 찾기'}
        />
      )}
    </>
  );
};

export default LoginForm;
