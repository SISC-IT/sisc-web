import styles from './PostItem.module.css';
import ProfileIcon from '../../assets/board_profile.svg';
import { getTimeAgo } from '../../utils/TimeUtils';

const PostItem = ({ post }) => {
  return (
    <div className={styles.postItem}>
      <div className={styles.header}>
        <img src={ProfileIcon} className={styles.authorImage} alt="프로필" />
        <span className={styles.author}>운영진</span>
        <span className={styles.dot}></span>
        <span className={styles.time}>{getTimeAgo(post.date)}</span>
      </div>
      <div className={styles.title}>{post.title}</div>
      <div className={styles.content}>{post.content}</div>
    </div>
  );
};

export default PostItem;
