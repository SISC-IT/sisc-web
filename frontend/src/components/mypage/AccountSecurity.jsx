import styles from './AccountSecurity.module.css';
import lockIcon from '../../assets/lock.svg';

const AccountSecurity = () => {
  return (
    <div className={styles.container}>
      <h2 className={styles.header}>계정 보안</h2>
      <p className={styles.description}>
        계정 보안을 위해 비밀번호를 정기적으로 변경하세요.
      </p>

      <button type="button" className={styles.button}>
        <img src={lockIcon} alt="잠금 아이콘" className={styles.icon} />
        <span className={styles.buttonName}>비밀번호 수정</span>
      </button>
    </div>
  );
};

export default AccountSecurity;
