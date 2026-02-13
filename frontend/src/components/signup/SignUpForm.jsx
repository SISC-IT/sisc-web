import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from '../LoginAndSignUpForm.module.css';
import sejong_logo from '../../assets/sejong_logo.png';
import { toast } from 'react-toastify';

import {
  sendVerificationNumber,
  signUp,
  checkVerificationNumber,
} from '../../utils/auth.js';

const passwordPolicy = [
  { label: '8~20자 이내', test: (pw) => pw.length >= 8 && pw.length <= 20 },
  { label: '최소 1개의 대문자 포함', test: (pw) => /[A-Z]/.test(pw) },
  { label: '최소 1개의 소문자 포함', test: (pw) => /[a-z]/.test(pw) },
  { label: '최소 1개의 숫자 포함', test: (pw) => /[0-9]/.test(pw) },
  { label: '최소 1개의 특수문자 포함', test: (pw) => /[\W_]/.test(pw) },
];

const SignUpForm = () => {
  const [studentName, setStudentName] = useState('');
  const [studentId, setStudentId] = useState('');
  const [verificationNumber, setVerificationNumber] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [gender, setGender] = useState('');
  const [college, setCollege] = useState('');
  const [department, setDepartment] = useState('');
  const [generation, setGeneration] = useState('');
  const [teamName, setTeamName] = useState('');
  const [remark, setRemark] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordValid, setPasswordValid] = useState(
    Array(passwordPolicy.length).fill(false)
  );

  const [isSending, setIsSending] = useState(false);
  const [isVerificationSent, setVerificationSent] = useState(false);
  const [isVerificationChecked, setVerificationChecked] = useState(false);

  const abortRef = useRef(null);
  const nav = useNavigate();

  const handlePasswordChange = (e) => {
    const newPassword = e.target.value;
    setPassword(newPassword);
    const newPasswordValid = passwordPolicy.map((rule) =>
      rule.test(newPassword)
    );
    setPasswordValid(newPasswordValid);
  };

  const isEmailValid = () => {
    const emailRegex = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/;
    return emailRegex.test(email);
  };

  const isPhoneNumberValid = () => {
    const phoneRegex = /^010-\d{4}-\d{4}$/;
    return phoneRegex.test(phoneNumber);
  };

  const isStudentIdValid = () => {
    const studentIdRegex = /^\d{8}$/;
    return studentIdRegex.test(studentId);
  };

  const isGenerationValid = () => {
    const generationRegex = /^\d+$/;
    return generationRegex.test(generation);
  };

  const areRequiredFieldsFilled =
    studentName.trim() !== '' &&
    studentId.trim() !== '' &&
    email.trim() !== '' &&
    phoneNumber.trim() !== '' &&
    gender !== '' &&
    college.trim() !== '' &&
    department.trim() !== '' &&
    generation.trim() !== '' &&
    teamName.trim() !== '' &&
    password !== '';

  const isPasswordValid = passwordValid.every(Boolean);

  const isFormValid =
    areRequiredFieldsFilled &&
    isStudentIdValid() &&
    isEmailValid() &&
    isVerificationSent &&
    isVerificationChecked &&
    isPhoneNumberValid() &&
    isGenerationValid() &&
    isPasswordValid &&
    password === confirmPassword;

  const handlePhoneChange = (e) => {
    const value = e.target.value.replace(/\D/g, '');

    let formattedValue = '';
    if (value.length <= 3) {
      formattedValue = value;
    } else if (value.length <= 7) {
      formattedValue = `${value.slice(0, 3)}-${value.slice(3)}`;
    } else {
      formattedValue = `${value.slice(0, 3)}-${value.slice(
        3,
        7
      )}-${value.slice(7, 11)}`;
    }

    setPhoneNumber(formattedValue);
  };

  const handleSendVerificationNumber = async (e) => {
    e.preventDefault();

    abortRef.current?.abort();
    abortRef.current = new AbortController();

    setIsSending(true);

    try {
      await sendVerificationNumber({ email }, abortRef.current.signal);
      setVerificationSent(true);
      toast.success('인증번호가 발송되었습니다.');
    } catch (error) {
      console.log(error);
      toast.error('오류가 발생했습니다.');
    } finally {
      setIsSending(false);
    }
  };

  const handleCheckVerificationNumber = async () => {
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    try {
      await checkVerificationNumber(
        { email, verificationNumber },
        abortRef.current.signal
      );
      setVerificationChecked(true);
      toast.success('인증되었습니다.');
    } catch (error) {
      console.log(error);
      toast.error('인증에 실패했습니다.');
    }
  };

  const handleSignUp = async (e) => {
    e.preventDefault();

    abortRef.current?.abort();
    abortRef.current = new AbortController();

    try {
      await signUp(
        {
          studentName,
          studentId,
          email,
          password,
          phoneNumber,
          gender,
          college,
          department,
          generation,
          teamName,
          remark,
        },
        abortRef.current.signal
      );
      toast.success('회원가입이 완료되었습니다.');
      nav('/login');
    } catch (error) {
      console.log(error);
      toast.error('회원가입에 실패하였습니다.');
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
            <label htmlFor="studentName">이름</label>
            <input
              type="text"
              id="studentName"
              value={studentName}
              onChange={(e) => setStudentName(e.target.value)}
              placeholder="이름을 입력해주세요"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="studentId">학번</label>
            <input
              type="text"
              id="studentId"
              value={studentId}
              onChange={(e) => setStudentId(e.target.value)}
              placeholder="학번을 입력해주세요"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="password">비밀번호</label>
            <input
              type="password"
              id="password"
              value={password}
              onChange={handlePasswordChange}
              placeholder="비밀번호를 입력해주세요"
              autoComplete="new-password"
            />
            <ul className={styles.passwordPolicy}>
              {passwordPolicy.map((rule, index) => (
                <li
                  key={rule.label}
                  className={passwordValid[index] ? styles.valid : ''}
                >
                  {rule.label}
                </li>
              ))}
            </ul>
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
                disabled={!isEmailValid() || isSending}
              >
                {isSending
                  ? '전송 중...'
                  : isVerificationSent
                    ? '재전송'
                    : '인증번호 발송'}
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
              onChange={handlePhoneChange}
              placeholder="ex) 01012345678"
              autoComplete="tel"
            />
          </div>
          <div className={styles.radioGroup}>
            <label>성별</label>
            <div className={styles.radioOptions}>
              <input
                type="radio"
                id="male"
                name="gender"
                value="male"
                checked={gender === 'male'}
                onChange={(e) => setGender(e.target.value)}
              />
              <label htmlFor="male">남성</label>

              <input
                type="radio"
                id="female"
                name="gender"
                value="female"
                checked={gender === 'female'}
                onChange={(e) => setGender(e.target.value)}
              />
              <label htmlFor="female">여성</label>
            </div>
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="college">단과대학</label>
            <input
              type="text"
              id="college"
              value={college}
              onChange={(e) => setCollege(e.target.value)}
              placeholder="ex) 소프트웨어융합대학"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="department">학과</label>
            <input
              type="text"
              id="department"
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
              placeholder="ex) 컴퓨터공학과"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="generation">기수</label>
            <input
              type="text"
              id="generation"
              value={generation}
              onChange={(e) => setGeneration(e.target.value)}
              placeholder="ex) 25"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="teamName">팀</label>
            <input
              type="text"
              id="teamName"
              value={teamName}
              onChange={(e) => setTeamName(e.target.value)}
              placeholder="ex) 금융IT"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="remark">특이사항</label>
            <input
              type="text"
              id="remark"
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              placeholder="특이사항이 있으면 작성해주세요"
            />
          </div>
          <button
            type="submit"
            className={styles.signUpButton}
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
