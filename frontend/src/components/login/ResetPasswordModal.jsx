import { useRef, useState } from 'react';
import styles from '../VerificationModal.module.css';
import { toast } from 'react-toastify';
import { resetPassword } from '../../utils/auth';

const ResetPasswordModal = ({ onClose }) => {
  const [email, setEmail] = useState('');

  // 이메일 형식 검증 함수
  const isValidEmail = (email) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const abortRef = useRef(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    if (!isValidEmail(email)) {
      toast.error('이메일 형식이 올바르지 않습니다.');
      return;
    }
    try {
      await resetPassword({ email }, abortRef.current.signal);
      toast.success('비밀번호 재설정 메일을 전송했습니다.');
      onClose();
    } catch (err) {
      console.dir(err);
      toast.error(
        '비밀번호 재설정 메일 전송에 실패했습니다. 다시 시도해주세요.'
      );
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
        <form onSubmit={handleSubmit}>
          <div className={styles.inputGroup}>
            <label htmlFor="email-address">
              등록하신 이메일 주소를 입력해주세요
            </label>
            <input
              type="email"
              id="email-address"
              className={`${styles.codeInput}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className={styles.buttonGroup}>
            <button
              type="submit"
              className={`${styles.button} ${styles.resetPasswordButton}`}
              disabled={!isValidEmail(email)}
            >
              제출
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ResetPasswordModal;
