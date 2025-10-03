import styles from '../VerificationModal.module.css';

const FindEmailResultModal = ({ onClose, onSuccess, result }) => {
  const handleSubmit = (e) => {
    e.preventDefault();

    onSuccess(); // 부모에게 성공 알림
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.popup}>
        <div style={{ textAlign: 'center', padding: '1rem' }}>
          <p>회원님의 아이디는 아래와 같습니다.</p>
          <strong style={{ fontSize: '1.2rem' }}>{result}</strong>
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
      </div>
    </div>
  );
};

export default FindEmailResultModal;
