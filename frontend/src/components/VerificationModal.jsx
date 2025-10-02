import { useState } from 'react';
import styles from './VerificationModal.module.css';

// 로그인 페이지 - 이메일 찾기, 비밀번호 찾기
// 회원가입 페이지 - 이메일 인증

const VerificationModal = ({ onClose, onEmailVerified, title }) => {
  const [code, setCode] = useState('');
  const [isCodeSent, setIsCodeSent] = useState(false);
  const [isButtonDisabled, setIsButtonDisabled] = useState(false);

  const handleSendCode = () => {
    // 3초정도 대기하는 로직 추가 필요
    setIsButtonDisabled(true);

    setTimeout(() => {
      setIsButtonDisabled(false);
    }, 3000);

    if (!isCodeSent) {
      alert('인증번호가 전송되었습니다.');
      setIsCodeSent(true); // 최초 1회만 "인증번호 보내기" 메시지이고, 이후 "재전송"으로 뜨게 설정
    } else {
      alert('인증번호가 재전송되었습니다.');
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    alert(`입력된 인증번호: ${code}`);

    // 이메일 인증 성공 시 수행
    // onEmailVerified(); // 부모의 setConfirmEmail() 호출 - 회원가입:이메일 인증 시에만 사용
    onClose(); // 팝업 닫기
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.popup}>
        <h1>{title}</h1>
        <form onSubmit={handleSubmit}>
          <div className={styles.inputGroup}>
            <label htmlFor="verification-code" className={styles.label}>
              인증번호
            </label>
            <div className={styles.verificationContainer}>
              <input
                type="text"
                id="verification-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="인증번호를 입력하세요"
                className={styles.codeInput}
              />
              <button
                type="button"
                className={`${styles.button} ${styles.sendButton}`}
                onClick={handleSendCode}
                disabled={isButtonDisabled}
              >
                {isButtonDisabled
                  ? '전송 중...'
                  : isCodeSent
                    ? '재전송'
                    : '인증번호 보내기'}
              </button>
            </div>
          </div>
          <div className={styles.buttonGroup}>
            <button
              type="submit"
              className={`${styles.button} ${styles.submitButton}`}
            >
              제출
            </button>
            <button
              type="button"
              className={`${styles.button} ${styles.closeButton}`}
              onClick={onClose}
            >
              닫기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default VerificationModal;
