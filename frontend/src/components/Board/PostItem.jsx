import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import styles from './PostItem.module.css';
import ProfileIcon from '../../assets/board_profile.svg';
import BookmarkIcon from '../../assets/boardBookMark.svg';
import BookmarkFilledIcon from '../../assets/boardBookMark.fill.svg';
import HeartIcon from '../../assets/boardHeart.svg';
import HeartFilledIcon from '../../assets/boardHeart.fill.svg';
import { getTimeAgo } from '../../utils/TimeUtils';
import { toBoardRouteSegment } from '../../utils/boardRoute';

const PostItem = ({ post, onLike, onBookmark }) => {
  const navigate = useNavigate();
  const { team } = useParams();

  const postId = post.postId || post.id;
  const boardName = post.boardName || post.board?.boardName;
  const authorName = post.user?.name || post.userName || '익명';
  const createdAt = post.createdDate || post.createdAt || post.date;
  const likeCount = Number(post.likeCount || 0);
  const bookmarkCount = Number(post.bookmarkCount || 0);

  const handleCardClick = () => {
    if (!postId) {
      console.error('게시글 ID를 찾을 수 없습니다:', post);
      alert('게시글 ID를 찾을 수 없습니다.');
      return;
    }

    const teamPath = toBoardRouteSegment(boardName) || team;

    if (!teamPath) {
      alert('게시판 정보를 찾을 수 없습니다.');
      return;
    }

    const path = `/board/${encodeURIComponent(teamPath)}/post/${postId}`;

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
    <div className={styles.postItem} onClick={handleCardClick}>
      <div className={styles.mainContent}>
        <div className={styles.leftSection}>
          <img src={ProfileIcon} className={styles.authorImage} alt="프로필" />
        </div>

        <div className={styles.contentSection}>
          <div className={styles.header}>
            <div className={styles.metaInfo}>
              <span className={styles.author}>{authorName}</span>
              <span className={styles.time}>{getTimeAgo(createdAt)}</span>
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
            {bookmarkCount > 0 && (
              <span className={styles.count}>{bookmarkCount}</span>
            )}
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
            {likeCount > 0 && <span className={styles.count}>{likeCount}</span>}
          </button>
        </div>
      </div>
    </div>
  );
};

PostItem.displayName = 'PostItem';

export default React.memo(PostItem);
