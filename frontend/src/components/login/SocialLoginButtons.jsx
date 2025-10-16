import styles from './SocialLoginButtons.module.css';

import googleIcon from './../../assets/google.png';
import kakaoIcon from './../../assets/kakao.png';
import githubIcon from './../../assets/github.png';

const SocialLoginButtons = ({ onGoogle, onGithub, onKakao }) => {
  return (
    <div className={styles.socialContainer} aria-label="소셜 로그인">
      <div className={styles.buttonGroup}>
        <button
          type="button"
          className={`${styles.btn} ${styles.google}`}
          onClick={onGoogle}
          alt="구글로 로그인"
        >
          <img src={googleIcon} />
          <div className={styles.btnText}>Google로 로그인하기</div>
        </button>

        <button
          type="button"
          className={`${styles.btn} ${styles.github}`}
          onClick={onGithub}
          alt="깃허브로 로그인"
        >
          <img src={githubIcon} />
          <div className={`${styles.btnText} ${styles.githubBtnText}`}>
            Github로 로그인하기
          </div>
        </button>

        <button
          type="button"
          className={`${styles.btn} ${styles.kakao}`}
          onClick={onKakao}
          alt="카카오로 로그인"
        >
          <img src={kakaoIcon} />
          <div className={styles.btnText}>kakao로 로그인하기</div>
        </button>
      </div>
    </div>
  );
};

export default SocialLoginButtons;
