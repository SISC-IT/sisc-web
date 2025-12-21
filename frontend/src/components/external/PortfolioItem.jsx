import styles from './PortfolioItem.module.css';
import profile from '../../assets/profile-image.png';

const PortfolioItem = ({ data }) => {
  return (
    <div className={styles.container}>
      <div className={styles.preview}>
        <img src={profile} alt="프로필 이미지" className={styles.image} />
        <div className={styles.titleSection}>
          <div className={styles.author}>
            <span className={styles.role}>{data.role}</span>
            <span className={styles.time}>{data.time}분전</span>
          </div>
          <span className={styles.title}>{data.title}</span>
        </div>
      </div>
    </div>
  );
};

export default PortfolioItem;
