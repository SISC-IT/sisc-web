import { useState } from 'react';
import styles from './EditProfileModal.module.css';
import EmailVerify from './EmailVerify';
import ChangeInfoForm from './ChangeInfoForm';
import rightArrow from '../../assets/right-arrow.svg';
import { toast } from 'react-toastify';
import { updateUserDetails } from '../../utils/userApi';

export default function EditProfileModal({ onClose }) {
  const [mode, setMode] = useState('menu');
  const [step, setStep] = useState('verify');

  const [verifiedEmail, setVerifiedEmail] = useState(null);
  const [passwordData, setPasswordData] = useState(null);

  const [formValid, setFormValid] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleMenuSelect = (nextMode) => {
    setMode(nextMode);
    setStep('verify');
    setVerifiedEmail(null);
    setPasswordData(null);
    setFormValid(false);
  };

  const getHeaderTitle = () => {
    if (mode === 'email') return '이메일 변경하기';
    if (mode === 'password') return '비밀번호 변경하기';
    return '개인정보 수정하기';
  };

  const renderContent = () => {
    if (mode === 'menu') {
      return (
        <div className={styles.menuList}>
          <button
            className={styles.menuItem}
            onClick={() => handleMenuSelect('email')}
          >
            이메일 변경하기
            <img src={rightArrow} alt=">" />
          </button>

          <button
            className={styles.menuItem}
            onClick={() => handleMenuSelect('password')}
          >
            비밀번호 변경하기
            <img src={rightArrow} alt=">" />
          </button>
        </div>
      );
    }

    if (step === 'verify') {
      return (
        <EmailVerify
          type={mode === 'email' ? 'newEmail' : 'currentEmail'}
          onVerified={(email) => setVerifiedEmail(email)}
        />
      );
    }

    if (step === 'form') {
      return (
        <ChangeInfoForm
          onValidChange={(data) => {
            setPasswordData(data);
            setFormValid(true);
          }}
        />
      );
    }
  };

  const getButtonText = () => {
    if (mode === 'password' && step === 'verify') return '계속하기';
    if (mode === 'password' && step === 'form') return '비밀번호 변경하기';
    if (mode === 'email') return '이메일 변경하기';
    return null;
  };

  const isButtonEnabled = () => {
    if (mode === 'password' && step === 'verify') return !!verifiedEmail;
    if (mode === 'password' && step === 'form') return formValid;
    if (mode === 'email') return !!verifiedEmail;
    return false;
  };

  const handleSubmit = async () => {
    if (mode === 'password' && step === 'verify') {
      setStep('form');
      return;
    }

    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      if (mode === 'password' && step === 'form') {
        await updateUserDetails({
          currentPassword: passwordData.currentPassword,
          newPassword: passwordData.newPassword,
        });

        toast.success('비밀번호가 변경되었습니다.');
        onClose();
        return;
      }

      if (mode === 'email') {
        await updateUserDetails({
          email: verifiedEmail,
        });

        toast.success('이메일이 변경되었습니다.');
        onClose();
      }
    } catch (error) {
      toast.error('변경에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const buttonText = getButtonText();

  return (
    <div className={styles.overlay} role="presentation">
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-profile-modal-title"
      >
        <div className={styles.modalHeaderColumn}>
          <div className={styles.modalHeader}>
            <h1>{getHeaderTitle()}</h1>
          </div>
        </div>

        {renderContent()}

        <div className={styles.modalFooter}>
          <button
            className={`${styles.cancelButton} ${
              buttonText ? styles.halfButton : ''
            }`}
            onClick={onClose}
          >
            취소
          </button>
          {buttonText && (
            <button // 계속하기 or 비밀번호 변경하기 or 이메일 변경하기 button
              className={`${styles.primaryButton} ${styles.halfButton}`}
              disabled={!isButtonEnabled() || isSubmitting}
              onClick={handleSubmit}
            >
              {isSubmitting ? '처리 중...' : buttonText}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
