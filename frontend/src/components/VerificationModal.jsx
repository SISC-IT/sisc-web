import { useState } from 'react';
import styles from './VerificationModal.module.css';

const VerificationModal = ({ title, onClose, onSuccess }) => {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [code, setCode] = useState('');
  const [isCodeSent, setIsCodeSent] = useState(false);
  const [sendButtonDisabled, setSendButtonDisabled] = useState(false);

  // 전화번호를 입력받아 코드를 보내는 함수
  const handleSendCode = async () => {
    // 전화번호 형식에 맞지 않을 시 return
    const phoneRegex = /^01[0-9]{1}[0-9]{7,8}$/;
    if (!phoneNumber || !phoneRegex.test(phoneNumber)) {
      alert('올바른 전화번호 형식을 입력해주세요. (예: 01012345678)');
      return;
    }

    setSendButtonDisabled(true);
    console.log(`${phoneNumber}로 인증번호 전송 API 호출`);
    // 실제 api 추가

    alert('인증번호가 전송되었습니다.');
    setIsCodeSent(true);
    setTimeout(() => setSendButtonDisabled(false), 3000);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!code) {
      alert('인증번호를 입력해주세요.');
      return;
    }
    console.log(`${phoneNumber}와 ${code}로 인증 확인 API 호출`);

    try {
      // <<-- 실제 API 호출 로직: const result = await api.verifyCode(phoneNumber, code) -->>

      const mockResult = { email: 'user@example.com', message: '인증 성공' };
      alert('인증에 성공했습니다!');
      onSuccess(mockResult);
    } catch (error) {
      console.error('인증 실패:', error);
      alert('인증번호가 올바르지 않습니다.');
    }
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <h1>{title}</h1>
        <form onSubmit={handleSubmit}>
          <div className={styles.inputGroup}>
            <label htmlFor="phone-number">전화번호 인증</label>
            <div className={styles.verificationContainer}>
              <input
                type="tel"
                id="phone-number"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                placeholder="'-' 없이 입력"
                className={styles.codeInput}
              />
              <button
                type="button"
                onClick={handleSendCode}
                disabled={sendButtonDisabled}
                className={`${styles.button} ${styles.sendButton}`}
              >
                {sendButtonDisabled
                  ? '전송 중...'
                  : isCodeSent
                    ? '재전송'
                    : '인증번호 전송'}
              </button>
            </div>
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="verification-code">인증번호</label>
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
                type="submit"
                className={`${styles.button} ${styles.submitButton}`}
                disabled={!isCodeSent}
              >
                인증 확인
              </button>
            </div>
          </div>

          <div className={styles.buttonGroup}>
            <button
              type="button"
              onClick={onClose}
              className={`${styles.button} ${styles.closeButton}`}
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
