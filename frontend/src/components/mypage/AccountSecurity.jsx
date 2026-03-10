import { useState } from 'react';
import styles from './AccountSecurity.module.css';
import settingIcon from '../../assets/setting_icon.svg';
import EditProfileModal from './EditProfileModal';

const AccountSecurity = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <>
      <div className={styles.container}>
        <button
          type="button"
          className={styles.button}
          onClick={() => setIsModalOpen(true)}
        >
          <img src={settingIcon} alt="설정 아이콘" className={styles.icon} />
          <span className={styles.buttonName}>개인정보 수정하기</span>
        </button>
      </div>

      {isModalOpen && (
        <EditProfileModal onClose={() => setIsModalOpen(false)} />
      )}
    </>
  );
};

export default AccountSecurity;
