import styles from './ConfirmationToast.module.css';

const ConfirmationToast = ({ closeToast, onConfirm, message }) => {
  return (
    <div className={styles.toastContainer}>
      <p className={styles.message}>{message}</p>
      <div className={styles.buttonGroup}>
        <button
          className={`${styles.button} ${styles.confirmButton}`}
          onClick={() => {
            onConfirm();
            closeToast();
          }}
        >
          예
        </button>
        <button
          className={`${styles.button} ${styles.cancelButton}`}
          onClick={closeToast}
        >
          아니오
        </button>
      </div>
    </div>
  );
};

export default ConfirmationToast;
