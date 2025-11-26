import styles from './Field.module.css';

const Field = ({ label, children }) => {
  return (
    <label className={styles.field}>
      <span className={styles.fieldLabel}>{label}</span>
      <div className={styles.fieldControl}>{children}</div>
    </label>
  );
};

export default Field;
