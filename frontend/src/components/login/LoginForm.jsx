import React, { useState } from 'react';
import { useNavigate, NavLink } from 'react-router-dom';
import styles from './LoginForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';

import SocialLoginButtons from './SocialLoginButtons';

const LoginForm = () => {
  const [id, setId] = useState('');
  const [password, setPassword] = useState('');
  const nav = useNavigate();

  const isFormValid = id.trim() !== '' && password.trim() !== '';

  const handleLogin = (e) => {
    e.preventDefault();

    // 안전장치
    if (!id || !password) {
      alert('아이디와 비밀번호를 모두 입력해주세요.');
      return;
    }

    // console.log(id, password); // 디버그용
    localStorage.setItem('authToken', 'dummy-token-12345');
    nav('/'); // 임시 페이지 이동
  };

  return (
    <div className={styles.formContainer}>
      <form className={styles.loginForm} onSubmit={handleLogin}>
        <div className={styles.logoBox}>
          <img src={sejong_logo} alt="sejong_logo" className={styles.logo} />
        </div>

        <h2>Sejong Investment Scholars Club</h2>
        <div className={styles.inputGroup}>
          <label htmlFor="email">Email</label>
          <input
            type="text"
            id="email"
            value={id}
            onChange={(e) => setId(e.target.value)}
            placeholder="이메일을 입력하세요"
            autoComplete="username"
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
      <nav className={styles.findContainer}>
        <NavLink to="/findid" className={styles.text}>
          아이디 찾기
        </NavLink>
        <span className={styles.divider} aria-hidden="true">
          |
        </span>
        <NavLink to="/findpassword" className={styles.text}>
          비밀번호 찾기
        </NavLink>
      </nav>

      {/* 소셜 로그인 버튼들 */}
      <SocialLoginButtons />
    </div>
  );
};

export default LoginForm;
