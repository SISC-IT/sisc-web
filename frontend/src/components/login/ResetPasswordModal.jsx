import { useState } from 'react';
import styles from '../VerificationModal.module.css';

const ResetPasswordModal = ({ onClose }) => {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (password !== confirmPassword) {
      alert('비밀번호가 일치하지 않습니다.');
      return;
    }
    if (password.length < 8) {
      alert('비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    console.log('비밀번호 재설정 API 호출');
    alert('비밀번호가 성공적으로 재설정되었습니다.');
    onClose(); // 부모에게 성공 알림
  };

  const isPasswordValid = password == confirmPassword && password.length >= 8;

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <h1>비밀번호 재설정</h1>
        <form onSubmit={handleSubmit}>
          <div className={styles.inputGroup}>
            <label htmlFor="new-password">새 비밀번호</label>
            <input
              type="password"
              id="new-password"
              className={`${styles.codeInput} ${styles.newPassword}`}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="8자 이상 입력"
            />
          </div>
          <div className={styles.inputGroup}>
            <label htmlFor="confirm-password">새 비밀번호 확인</label>
            <input
              type="password"
              id="confirm-password"
              className={`${styles.codeInput} ${styles.newPassword}`}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="비밀번호를 다시 입력하세요"
            />
          </div>
          <div className={styles.buttonGroup}>
            <button
              type="submit"
              className={`${styles.button} ${styles.resetPasswordButton}`}
              disabled={!isPasswordValid}
            >
              비밀번호 재설정
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ResetPasswordModal;
