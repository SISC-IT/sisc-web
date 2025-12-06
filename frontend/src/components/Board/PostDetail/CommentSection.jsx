import React, { useState } from 'react';
import styles from '../../../pages/PostDetail.module.css';
import ProfileIcon from '../../../assets/board_profile.svg';
import EditIcon from '../../../assets/boardPencil.svg';
import DeleteIcon from '../../../assets/boardCloseIcon.svg';
import BookmarkIcon from '../../../assets/boardBookMark.svg';
import BookmarkFilledIcon from '../../../assets/boardBookMark.fill.svg';
import HeartIcon from '../../../assets/boardHeart.svg';
import HeartFilledIcon from '../../../assets/boardHeart.fill.svg';
import { getTimeAgo } from '../../../utils/TimeUtils';
const CommentItem = ({
  comment,
  onReplyClick,
  onUpdateComment,
  onDeleteComment,
  isReplying,
  onSubmitReply,
  showCommentMenu,
  setShowCommentMenu,
  replyTargetId,
  depth = 0,
}) => {
  const commentId = comment.commentId || comment.id;
  const author =
    comment.author || comment.user?.name || comment.createdBy?.name || '사용자';
  const text = comment.text || comment.content;
  const date = comment.createdDate || comment.createdAt || comment.date;

  const [localReplyText, setLocalReplyText] = useState('');

  React.useEffect(() => {
    if (!isReplying) setLocalReplyText('');
  }, [isReplying]);

  const handleLocalSubmit = () => {
    onSubmitReply(commentId, localReplyText);
  };

  return (
    <div className={styles.commentItemWrapper}>
      <div className={styles.commentCard}>
        <div className={styles.commentHeader}>
          <div className={styles.commentMeta}>
            <img
              src={ProfileIcon}
              className={styles.profileIcon}
              alt="프로필"
            />
            <p className={styles.commentAuthor}>{author}</p>
          </div>
          <p className={styles.commentDate}>{getTimeAgo(date)}</p>

          <div className={styles.menuContainer}>
            <button
              className={styles.menuButton}
              onClick={() => setShowCommentMenu(commentId)}
              aria-label="댓글 메뉴"
            >
              ⋮
            </button>
            {showCommentMenu === commentId && (
              <div className={styles.menuDropdown}>
                <button onClick={() => onUpdateComment(commentId)}>
                  <img src={EditIcon} className={styles.EditIcon} alt="수정" />{' '}
                  수정하기
                </button>
                <button onClick={() => onDeleteComment(commentId)}>
                  <img
                    src={DeleteIcon}
                    className={styles.DeleteIcon}
                    alt="삭제"
                  />{' '}
                  삭제하기
                </button>
              </div>
            )}
          </div>
        </div>

        <p className={styles.commentText}>{text}</p>

        {depth === 0 && (
          <button
            className={styles.replyButton}
            onClick={() => onReplyClick(commentId)}
          >
            답글 달기
          </button>
        )}
      </div>

      {depth === 0 && isReplying && (
        <div className={styles.replyInputWrapper}>
          <textarea
            className={styles.replyTextarea}
            placeholder="대댓글을 입력하세요."
            value={localReplyText}
            onChange={(e) => setLocalReplyText(e.target.value)}
          />
          <button
            type="button"
            className={styles.replySubmitButton}
            onClick={handleLocalSubmit}
            disabled={!localReplyText.trim()}
          >
            대댓글 등록
          </button>
        </div>
      )}

      {comment.replies && comment.replies.length > 0 && (
        <div className={styles.replyList}>
          {comment.replies.map((child) => (
            <CommentItem
              key={child.commentId || child.id}
              comment={child}
              onReplyClick={onReplyClick}
              onUpdateComment={onUpdateComment}
              onDeleteComment={onDeleteComment}
              isReplying={replyTargetId === (child.commentId || child.id)}
              onSubmitReply={onSubmitReply}
              showCommentMenu={showCommentMenu}
              setShowCommentMenu={setShowCommentMenu}
              replyTargetId={replyTargetId}
              depth={depth + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
};

const CommentSection = ({
  post,
  comments,
  onLike,
  onBookmark,
  onAddComment,
  onUpdateComment,
  onDeleteComment,
  onReplyClick,
  onSubmitReply,
  replyTargetId,
  showCommentMenu,
  setShowCommentMenu,
}) => {
  const [commentText, setCommentText] = useState('');

  const handleAdd = () => {
    onAddComment(commentText);
    setCommentText('');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && e.ctrlKey) handleAdd();
  };

  return (
    <div className={styles.commentsSection}>
      <div className={styles.commentsHeaderWrapper}>
        <div className={styles.actions}>
          <button className={styles.actionButton} onClick={onBookmark}>
            <img
              src={post.isBookmarked ? BookmarkFilledIcon : BookmarkIcon}
              alt="북마크"
            />
            {post.bookmarkCount > 0 && (
              <span className={styles.count}>{post.bookmarkCount}</span>
            )}
          </button>
          <button className={styles.actionButton} onClick={onLike}>
            <img
              src={post.isLiked ? HeartFilledIcon : HeartIcon}
              alt="좋아요"
            />
            {post.likeCount > 0 && (
              <span className={styles.count}>{post.likeCount}</span>
            )}
          </button>
        </div>
        <div className={styles.commentsHeader}>
          <p className={styles.commentCount}>
            댓글 <span>{comments.length}</span>
          </p>
        </div>
      </div>

      <div className={styles.commentInput}>
        <textarea
          className={styles.textarea}
          placeholder="댓글을 입력해주세요..."
          value={commentText}
          onChange={(e) => setCommentText(e.target.value)}
          onKeyDown={handleKeyDown}
        />
      </div>
      <button
        className={styles.submitButton}
        onClick={handleAdd}
        disabled={!commentText.trim()}
      >
        댓글 남기기
      </button>

      <div className={styles.commentsList}>
        {comments.length > 0 ? (
          comments.map((comment) => (
            <CommentItem
              key={comment.commentId || comment.id}
              comment={comment}
              onReplyClick={onReplyClick}
              onUpdateComment={onUpdateComment}
              onDeleteComment={onDeleteComment}
              isReplying={replyTargetId === (comment.commentId || comment.id)}
              onSubmitReply={onSubmitReply}
              showCommentMenu={showCommentMenu}
              setShowCommentMenu={setShowCommentMenu}
              replyTargetId={replyTargetId}
            />
          ))
        ) : (
          <p className={styles.noComments}>댓글이 없습니다.</p>
        )}
      </div>
    </div>
  );
};

export default CommentSection;
