import { useState, useRef, useEffect } from 'react';
import {
  sendVerificationNumber,
  checkVerificationNumber,
} from '../../utils/auth';
import styles from './EditProfileModal.module.css';
import { toast } from 'react-toastify';

export default function EmailVerify({ onVerified, type }) {
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');

  const [isCodeSent, setIsCodeSent] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [isSending, setIsSending] = useState(false);

  const [timeLeft, setTimeLeft] = useState(180);

  const abortRef = useRef(null);

  const isEmailValid = (email) =>
    /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(email);

  useEffect(() => {
    if (!isCodeSent || isVerified) return;

    const timer = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [isCodeSent, isVerified]);

  const formatTime = (time) => {
    const min = Math.floor(time / 60);
    const sec = time % 60;
    return `${min}:${sec.toString().padStart(2, '0')}`;
  };

  const handleSend = async () => {
    if (!isEmailValid(email)) {
      toast.error('올바른 이메일 형식이 아닙니다.');
      return;
    }

    abortRef.current?.abort();
    abortRef.current = new AbortController();

    setIsSending(true);

    try {
      await sendVerificationNumber({ email });
      setIsCodeSent(true);
      setTimeLeft(180);
      toast.success('인증번호가 발송되었습니다.');
    } catch {
      toast.error('인증번호 발송 실패');
    } finally {
      setIsSending(false);
    }
  };

  const handleVerify = async () => {
    try {
      await checkVerificationNumber({
        email,
        verificationNumber: code,
      });

      setIsVerified(true);
      toast.success('이메일 인증 완료');

      onVerified(email);
    } catch {
      toast.error('인증번호가 올바르지 않습니다.');
    }
  };

  return (
    <div className={styles.modalContent}>
      <div className={styles.inputGroup}>
        <label className={styles.label}>
          {type === 'newEmail' ? '새 이메일' : 'Email'}
        </label>

        <div className={styles.verificationContainer}>
          <input
            className={styles.codeInput}
            type="email"
            value={email}
            disabled={isVerified}
            onChange={(e) => setEmail(e.target.value)}
          />

          <button
            className={`${styles.button} ${styles.sendButton}`}
            onClick={handleSend}
            disabled={!email || !isEmailValid(email) || isSending || isVerified}
          >
            {isSending ? '전송 중...' : isCodeSent ? '재전송' : '인증번호 발송'}
          </button>
        </div>
      </div>

      {isCodeSent && (
        <div className={styles.inputGroup}>
          <label className={styles.label}>인증번호</label>

          <div className={styles.verificationContainer}>
            <input
              className={styles.codeInput}
              value={code}
              disabled={isVerified}
              maxLength={6}
              onChange={(e) => setCode(e.target.value)}
            />

            {!isVerified && (
              <span className={styles.timer}>{formatTime(timeLeft)}</span>
            )}

            <button
              className={`${styles.button} ${styles.submitButton}`}
              onClick={handleVerify}
              disabled={!code || isVerified}
            >
              인증번호 확인
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
