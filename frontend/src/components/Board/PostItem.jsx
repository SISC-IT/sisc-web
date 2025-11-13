import React from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './PostItem.module.css';
import ProfileIcon from '../../assets/board_profile.svg';
import BookmarkIcon from '../../assets/boardBookMark.svg';
import BookmarkFilledIcon from '../../assets/boardBookmark.fill.svg';
import HeartIcon from '../../assets/boardHeart.svg';
import HeartFilledIcon from '../../assets/boardHeart.fill.svg';
import { getTimeAgo } from '../../utils/TimeUtils';

const PostItem = React.memo(({ post, onLike, onBookmark }) => {
  const navigate = useNavigate();

  const handleCardClick = () => {
    navigate(`/board/${post.id}`, { state: { post } });
  };

  const handleActionClick = (e) => {
    e.stopPropagation();
  };

  return (
    <div
      className={styles.postItem}
      onClick={handleCardClick}
      style={{ cursor: 'pointer' }}
    >
      <div className={styles.mainContent}>
        <div className={styles.leftSection}>
          <img src={ProfileIcon} className={styles.authorImage} alt="프로필" />
        </div>

        <div className={styles.contentSection}>
          <div className={styles.header}>
            <div className={styles.metaInfo}>
              <span className={styles.author}>{post.author || '운영진'}</span>
              <span className={styles.time}>{getTimeAgo(post.date)}</span>
            </div>
          </div>
          <div className={styles.title}>{post.title}</div>
          <div className={styles.content}>{post.content}</div>
        </div>

        <div className={styles.actions} onClick={handleActionClick}>
          <button
            className={styles.actionButton}
            onClick={() => onBookmark(post.id)}
            aria-label="북마크"
          >
            <img
              src={post.isBookmarked ? BookmarkFilledIcon : BookmarkIcon}
              alt="북마크"
            />
            {post.bookmarkCount > 0 && (
              <span className={styles.count}>{post.bookmarkCount}</span>
            )}
          </button>
          <button
            className={styles.actionButton}
            onClick={() => onLike(post.id)}
            aria-label="좋아요"
          >
            <img
              src={post.isLiked ? HeartFilledIcon : HeartIcon}
              alt="좋아요"
            />
            {post.likeCount > 0 && (
              <span className={styles.count}>{post.likeCount}</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
});

PostItem.displayName = 'PostItem';

export default PostItem;
