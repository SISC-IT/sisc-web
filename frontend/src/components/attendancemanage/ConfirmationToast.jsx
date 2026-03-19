import styles from './ConfirmationToast.module.css';

const ConfirmationToast = ({
  closeToast,
  onConfirm,
  message,
  title,
  description,
  confirmLabel = '예',
  cancelLabel = '아니오',
  variant = 'default',
}) => {
  const heading = title || message;

  return (
    <div
      className={`${styles.toastContainer} ${
        variant === 'roleChange' ? styles.roleChangeContainer : ''
      }`}
    >
      <p
        className={`${styles.message} ${
          variant === 'roleChange' ? styles.roleChangeTitle : ''
        }`}
      >
        {heading}
      </p>
      {description ? <p className={styles.description}>{description}</p> : null}
      <div className={styles.buttonGroup}>
        {variant === 'roleChange' ? (
          <>
            <button
              className={`${styles.button} ${styles.cancelButton}`}
              onClick={() => closeToast?.()}
            >
              {cancelLabel}
            </button>
            <button
              className={`${styles.button} ${styles.roleConfirmButton}`}
              onClick={() => {
                onConfirm();
                closeToast?.();
              }}
            >
              {confirmLabel}
            </button>
          </>
        ) : (
          <>
            <button
              className={`${styles.button} ${styles.confirmButton}`}
              onClick={() => {
                onConfirm();
                closeToast?.();
              }}
            >
              {confirmLabel}
            </button>
            <button
              className={`${styles.button} ${styles.cancelButton}`}
              onClick={() => closeToast?.()}
            >
              {cancelLabel}
            </button>
          </>
        )}
      </div>
    </div>
  );
};

export default ConfirmationToast;
