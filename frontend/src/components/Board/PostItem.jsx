import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import styles from './PostItem.module.css';
import ProfileIcon from '../../assets/board_profile.svg';
import BookmarkIcon from '../../assets/boardBookMark.svg';
import BookmarkFilledIcon from '../../assets/boardBookMark.fill.svg';
import HeartIcon from '../../assets/boardHeart.svg';
import HeartFilledIcon from '../../assets/boardHeart.fill.svg';
import { getTimeAgo } from '../../utils/TimeUtils';

const PostItem = ({ post, onLike, onBookmark }) => {
  const navigate = useNavigate();
  const { team } = useParams();

  const postId = post.postId || post.id;

  const handleCardClick = () => {
    if (!postId) {
      console.error('게시글 ID를 찾을 수 없습니다:', post);
      alert('게시글 ID를 찾을 수 없습니다.');
      return;
    }

    const nameToPath = {
      증권1팀: 'securities-1',
      증권2팀: 'securities-2',
      증권3팀: 'securities-3',
      자산운용: 'asset-management',
      금융IT: 'finance-it',
      매크로: 'macro',
      트레이딩: 'trading',
    };

    const boardName = post.boardName || post.board?.boardName;
    const teamPath = nameToPath[boardName] || team;

    if (!teamPath) {
      alert('게시판 정보를 찾을 수 없습니다.');
      return;
    }

    const path = teamPath
      ? `/board/${teamPath}/post/${postId}`
      : `/board/post/${postId}`;

    navigate(path, { state: { post } });
  };

  const handleBookmarkClick = (e) => {
    e.stopPropagation();
    e.preventDefault();

    if (!postId) {
      console.error('북마크 실패: 게시글 ID 없음');
      return;
    }

    onBookmark(postId);
  };

  const handleLikeClick = (e) => {
    e.stopPropagation();
    e.preventDefault();

    if (!postId) {
      console.error('좋아요 실패: 게시글 ID 없음');
      return;
    }

    onLike(postId);
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
              <span className={styles.author}>운영진</span>
              <span className={styles.time}>{getTimeAgo(post.date)}</span>
            </div>
          </div>
          <div className={styles.title}>{post.title}</div>
          <div className={styles.content}>{post.content}</div>
        </div>

        <div className={styles.actions}>
          <button
            className={styles.actionButton}
            onClick={handleBookmarkClick}
            aria-label="북마크"
          >
            <img
              src={post.isBookmarked ? BookmarkFilledIcon : BookmarkIcon}
              alt="북마크"
            />
            {post.isBookmarked && <span className={styles.count}>1</span>}
          </button>
          <button
            className={styles.actionButton}
            onClick={handleLikeClick}
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
};

PostItem.displayName = 'PostItem';

export default React.memo(PostItem);
