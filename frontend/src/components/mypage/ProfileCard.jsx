import { useState } from 'react';
import styles from './ProfileCard.module.css';
import ProfileImage from '../../assets/profile-image.png';
import Coin4 from '../../assets/coin4.svg';

const ProfileCard = () => {
  const [userName, setUserName] = useState('김성림');

  return (
    <section className={styles.profileRow} aria-label="프로필">
      <div>
        <img src={ProfileImage} alt="프로필 이미지" />
      </div>

      <div className={styles.textWrap}>
        <div className={styles.nameRow}>
          <h2 className={styles.username}>{userName}</h2>
          <button
            type="button"
            className={styles.iconBtn}
            aria-label="이름 수정"
            onClick={() => {
              // TODO: 이름 수정 모달 구현 (현재는 임시 prompt 사용)
              const newName = prompt('새 이름을 입력하세요:', userName);
              if (newName && newName.trim()) {
                setUserName(newName.trim());
              }
            }}
          >
            <span className={styles.nameChange}>
              이름 수정하기{' '}
              <span className={styles.chevronRight}>&gt;</span>{' '}
            </span>
          </button>
        </div>

        <div className={styles.metaRow}>
          <button
            type="button"
            className={styles.gradeBtn}
            aria-label="등급 보기"
          >
            일반 등급
          </button>

          <div className={styles.metaDivider} aria-hidden="true" />

          <div className={styles.points} aria-label="포인트">
            <img
              src={Coin4}
              alt="포인트 아이콘"
              className={styles.pointsIcon}
            />
            <p className={styles.pointsText}>300P</p>
          </div>
        </div>
      </div>
    </section>
  );
};

export default ProfileCard;
