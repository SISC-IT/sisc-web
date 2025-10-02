import styles from './ProfileCard.module.css';
import ProfileImage from '../../assets/profile-image.png';
import Coin4 from '../../assets/coin4.svg';
import PencilIcon from '../../assets/pencil-icon.svg';

const ProfileCard = () => {
  return (
    <section className={styles.profileRow} aria-label="프로필">
      <div>
        <img src={ProfileImage} alt="프로필 이미지" />
      </div>

      <div className={styles.textWrap}>
        <div className={styles.nameRow}>
          <h2 className={styles.username}>김성림</h2>
          <button
            type="button"
            className={styles.iconBtn}
            aria-label="이름 수정"
          >
            <img
              src={PencilIcon}
              className={styles.pencilIcon}
              alt="이름 수정 아이콘"
            />
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
