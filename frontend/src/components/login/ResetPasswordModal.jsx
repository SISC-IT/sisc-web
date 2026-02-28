import { useRef, useState } from 'react';
import styles from '../VerificationModal.module.css';
import { toast } from 'react-toastify';
import {
  confirmPasswordReset,
  sendPasswordResetCode,
} from '../../utils/auth';

const ResetPasswordModal = ({ onClose }) => {
  const [email, setEmail] = useState('');
  const [studentId, setStudentId] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [isCodeSent, setIsCodeSent] = useState(false);

  // 이메일 형식 검증 함수
  const isValidEmail = (email) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const isValidStudentId = (value) => /^\d{8}$/.test(value);

  const abortRef = useRef(null);

  const handleSendCode = async (e) => {
    e.preventDefault();
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    if (!isValidEmail(email) || !isValidStudentId(studentId)) {
      toast.error('이메일과 8자리 학번을 확인해주세요.');
      return;
    }

    try {
      await sendPasswordResetCode({ email, studentId }, abortRef.current.signal);
      setIsCodeSent(true);
      toast.success('인증코드를 전송했습니다.');
    } catch (err) {
      console.dir(err);
      toast.error('인증코드 전송에 실패했습니다. 다시 시도해주세요.');
    }
  };

  const handleConfirmReset = async (e) => {
    e.preventDefault();
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    if (!code.trim() || !newPassword.trim()) {
      toast.error('인증코드와 새 비밀번호를 입력해주세요.');
      return;
    }

    try {
      await confirmPasswordReset(
        { email, studentId, code, newPassword },
        abortRef.current.signal
      );
      toast.success('비밀번호가 변경되었습니다.');
      onClose();
    } catch (err) {
      console.dir(err);
      toast.error('비밀번호 변경에 실패했습니다. 입력값을 확인해주세요.');
    }
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <h1>비밀번호 초기화</h1>
          <button
            type="button"
            className={styles.closeButton}
            onClick={onClose}
          >
            &times;
          </button>
        </div>
        <form onSubmit={isCodeSent ? handleConfirmReset : handleSendCode}>
          <div className={styles.inputGroup}>
            <label htmlFor="email-address">등록한 이메일</label>
            <input
              type="email"
              id="email-address"
              className={`${styles.codeInput}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isCodeSent}
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="student-id">등록한 학번</label>
            <input
              type="text"
              id="student-id"
              className={`${styles.codeInput}`}
              value={studentId}
              onChange={(e) => {
                const value = e.target.value.replace(/\D/g, '');
                if (value.length <= 8) setStudentId(value);
              }}
              inputMode="numeric"
              maxLength={8}
              disabled={isCodeSent}
            />
          </div>

          {isCodeSent && (
            <>
              <div className={styles.inputGroup}>
                <label htmlFor="reset-code">인증코드</label>
                <input
                  type="text"
                  id="reset-code"
                  className={`${styles.codeInput}`}
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                />
              </div>
              <div className={styles.inputGroup}>
                <label htmlFor="new-password">새 비밀번호</label>
                <input
                  type="password"
                  id="new-password"
                  className={`${styles.codeInput}`}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                />
              </div>
            </>
          )}

          <div className={styles.buttonGroup}>
            <button
              type="submit"
              className={`${styles.button} ${styles.resetPasswordButton}`}
              disabled={
                isCodeSent
                  ? !code.trim() || !newPassword.trim()
                  : !isValidEmail(email) || !isValidStudentId(studentId)
              }
            >
              {isCodeSent ? '비밀번호 변경' : '인증코드 전송'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ResetPasswordModal;
