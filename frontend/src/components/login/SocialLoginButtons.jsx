import styles from './SocialLoginButtons.module.css';

const SocialLoginButtons = ({ onGoogle, onNaver, onKakao }) => {
  return (
    <div className={styles.socialContainer} aria-label="소셜 로그인">
      <div className={styles.divider}>
        <span>또는 간편 로그인</span>
      </div>

      <div className={styles.buttonGroup}>
        <button
          type="button"
          className={`${styles.btn} ${styles.google}`}
          onClick={onGoogle}
        >
          {/* <span className={styles.label}>Google</span> */}
        </button>

        <button
          type="button"
          className={`${styles.btn} ${styles.naver}`}
          onClick={onNaver}
        >
          {/* <span className={styles.label}>네이버</span> */}
        </button>

        <button
          type="button"
          className={`${styles.btn} ${styles.kakao}`}
          onClick={onKakao}
        >
          {/* <span className={styles.label}>카카오</span> */}
        </button>
      </div>
    </div>
  );
};

export default SocialLoginButtons;
