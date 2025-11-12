import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';
import EmailVerificationModal from './../VerificationModal';

const SignUpForm = () => {
  const [nickname, setNickname] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [verificationNumber, setVerificationNumber] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [isVerificationNumberSent, setVerificationNumberSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const nav = useNavigate();

  // 이메일 입력 형태가 맞는지 검사
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
    isPhoneNumberValid() &&
    password.trim() !== '' &&
    password === confirmPassword;

  const handleSendVerificationNumber = () => {
    // 전송 state 변경
    setVerificationNumberSent(true);

    // 인증번호 발송 로직
    alert('인증번호가 발송되었습니다.');
  };
  const handleSignUp = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setLoading(true);

    try {
      console.log('📋 회원가입 시작');
      console.log('닉네임:', nickname, '이메일:', email, '전화번호:', phoneNumber);

      const signupData = {
        name: nickname,  // 백엔드 필드명은 'name'
        email: email,
        password: password,
        phoneNumber: phoneNumber,
        role: 'TEAM_MEMBER',  // 기본 역할
      };

      console.log('🔄 회원가입 API 호출 중...', signupData);
      const response = await fetch('http://localhost:8080/api/auth/signup', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(signupData),
      });

      const data = await response.json();
      console.log('📨 백엔드 응답:', response.status, data);

      if (response.ok) {
        console.log('✅ 회원가입 성공:', data);
        alert('회원가입이 완료되었습니다. 로그인 페이지로 이동합니다.');
        nav('/login');
      } else {
        // 백엔드 에러 메시지 처리
        const errorMsg = data.message || '회원가입에 실패했습니다.';
        console.error('❌ 회원가입 실패:', errorMsg);
        setErrorMessage(errorMsg);
      }
    } catch (err) {
      console.error('❌ 회원가입 API 오류:', err.message);
      setErrorMessage('서버 연결 오류가 발생했습니다. 다시 시도해주세요.');
    } finally {
      setLoading(false);
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
            <label htmlFor="phoneNumber">휴대전화</label>
            <div className={styles.phoneVerificationContainer}>
              <input
                type="phoneNumber"
                id="text"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="ex) 01012345678"
                className={styles.phoneNumberInput}
              />
              <button
                type="button"
                className={styles.verifyButton}
                onClick={handleSendVerificationNumber}
                disabled={!isPhoneNumberValid()}
              >
                인증번호 발송
              </button>
            </div>
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="verificationNumber">인증번호</label>
            <input
              type="text"
              id="verificationNumber"
              value={verificationNumber}
              onChange={(e) => setVerificationNumber(e.target.value)}
              placeholder="인증번호를 입력해주세요"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="email">Email</label>
            <input
              type="email"
              id="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="이메일을 입력해주세요"
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
            {loading ? '가입 중...' : '회원가입'}
          </button>
        </form>
      </div>
    </>
  );
};

export default SignUpForm;
