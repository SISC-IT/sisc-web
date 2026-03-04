import styles from './AccountSecurity.module.css';
import lockIcon from '../../assets/lock.svg';

const AccountSecurity = () => {
  return (
    <div className={styles.container}>
      <button type="button" className={styles.button}>
        <img src={lockIcon} alt="잠금 아이콘" className={styles.icon} />
        <span className={styles.buttonName}>비밀번호 수정</span>
      </button>
    </div>
  );
};

export default AccountSecurity;
