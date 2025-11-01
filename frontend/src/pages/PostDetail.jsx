import React, { useState } from 'react';
import { useLocation } from 'react-router-dom';
import styles from './PostDetail.module.css';
import ProfileIcon from '../assets/board_profile.svg';
import { getTimeAgo } from '../utils/TimeUtils';

const PostDetail = () => {
  const location = useLocation();
  const post = location.state?.post;
  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState('');

  if (!post) {
    return (
      <div className={styles.container}>
        <p className={styles.notFound}>게시글을 찾을 수 없습니다.</p>
      </div>
    );
  }

  const handleAddComment = () => {
    if (!commentText.trim()) return;

    const newComment = {
      id: Date.now(),
      author: '사용자',
      text: commentText,
      date: new Date(),
    };
    setComments([...comments, newComment]);
    setCommentText('');
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && e.ctrlKey) {
      handleAddComment();
    }
  };

  return (
    <div className={styles.container}>
      <article className={styles.postDetail}>
        <h1 className={styles.title}>{post.title}</h1>
        <div className={styles.divider} />
        <div className={styles.meta}>
          <img src={ProfileIcon} className={styles.profileIcon} alt="프로필" />
          <div className={styles.metaInfo}>
            <p className={styles.author}>운영진</p>
            <p className={styles.date}>{getTimeAgo(post.date)}</p>
          </div>
        </div>
        <div className={styles.content}>{post.content}</div>

        <div className={styles.commentsSection}>
          <div className={styles.commentsHeader}>
            <p className={styles.commentCount}>
              댓글 <span>{comments.length}</span>
            </p>
          </div>

          <div className={styles.commentInput}>
            <textarea
              className={styles.textarea}
              placeholder="댓글을 입력해주세요. 허위사실, 욕설 등을 포함한 댓글은 별도의 안내 없이 삭제될 수 있어요."
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              onKeyPress={handleKeyPress}
            />
          </div>

          <button
            className={styles.submitButton}
            onClick={handleAddComment}
            disabled={!commentText.trim()}
          >
            댓글 남기기
          </button>

          <div className={styles.commentsList}>
            {comments.length > 0 ? (
              comments.map(({ id, author, text, date }) => (
                <div key={id} className={styles.commentCard}>
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
                  </div>
                  <p className={styles.commentText}>{text}</p>
                </div>
              ))
            ) : (
              <p className={styles.noComments}>댓글이 없습니다.</p>
            )}
          </div>
        </div>
      </article>
    </div>
  );
};

export default PostDetail;
