import styles from '../VerificationModal.module.css';

const FindEmailResultModal = ({ onClose, result }) => {
  const handleSubmit = (e) => {
    e.preventDefault();
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <h1 className={styles.findEmailH1}>이메일 찾기</h1>
        <div className={styles.textbox}>
          {result ? (
            <>
              <p>등록된 회원님의 이메일은</p>
              <p>{result}입니다.</p>
            </>
          ) : (
            <p>이메일 정보가 등록되어 있지 않습니다.</p>
          )}
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
