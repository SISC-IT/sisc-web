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
          <img
            src="https://upload.wikimedia.org/wikipedia/commons/c/c1/Google_%22G%22_logo.svg"
            alt="구글로 로그인"
          />
        </button>

        <button
          type="button"
          className={`${styles.btn} ${styles.naver}`}
          onClick={onNaver}
        ></button>

        <button
          type="button"
          className={`${styles.btn} ${styles.kakao}`}
          onClick={onKakao}
        ></button>
      </div>
    </div>
  );
};

export default SocialLoginButtons;
