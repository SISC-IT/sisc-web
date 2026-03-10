import { useState, useEffect } from 'react';
import styles from './EditProfileModal.module.css';

const passwordPolicy = [
  { label: '8~20자 이내', test: (pw) => pw.length >= 8 && pw.length <= 20 },
  { label: '최소 1개의 대문자 포함', test: (pw) => /[A-Z]/.test(pw) },
  { label: '최소 1개의 소문자 포함', test: (pw) => /[a-z]/.test(pw) },
  { label: '최소 1개의 숫자 포함', test: (pw) => /[0-9]/.test(pw) },
  { label: '최소 1개의 특수문자 포함', test: (pw) => /[\W_]/.test(pw) },
];

export default function ChangeInfoForm({ onValidChange }) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const passwordValid = passwordPolicy.every((p) => p.test(newPassword));

  const isValid =
    currentPassword && passwordValid && newPassword === confirmPassword;

  useEffect(() => {
    if (isValid) {
      onValidChange({
        currentPassword,
        newPassword,
      });
    }
  }, [currentPassword, newPassword, confirmPassword]);

  return (
    <div className={styles.modalContent}>
      <div className={styles.inputGroup}>
        <label className={styles.label}>현재 비밀번호</label>

        <input
          className={styles.codeInput}
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
        />
      </div>

      <div className={styles.inputGroup}>
        <label className={styles.label}>새 비밀번호</label>

        <input
          className={styles.codeInput}
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
      </div>

      <div className={styles.inputGroup}>
        <label className={styles.label}>비밀번호 확인</label>

        <input
          className={styles.codeInput}
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
        />
      </div>

      <div>
        {passwordPolicy.map((rule, index) => (
          <p key={index}>
            {rule.test(newPassword) ? '✅' : '❌'} {rule.label}
          </p>
        ))}
      </div>
    </div>
  );
}
