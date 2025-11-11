import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import styles from './PostDetail.module.css';

import ProfileIcon from '../assets/board_profile.svg';
import BookmarkIcon from '../assets/boardBookMark.svg';
import BookmarkFilledIcon from '../assets/boardBookmark.fill.svg';
import HeartIcon from '../assets/boardHeart.svg';
import HeartFilledIcon from '../assets/boardHeart.fill.svg';
import EditIcon from '../assets/boardPencil.svg';
import DeleteIcon from '../assets/boardCloseIcon.svg';
import { getTimeAgo } from '../utils/TimeUtils';

const PostDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);

  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState('');
  const [showMenu, setShowMenu] = useState(false);

  const getPostsFromStorage = () => {
    try {
      const saved = localStorage.getItem('boardPosts');
      if (!saved) return [];
      return JSON.parse(saved).map((p) => ({ ...p, date: new Date(p.date) }));
    } catch (error) {
      console.error('Failed to parse posts from localStorage', error);
      return [];
    }
  };

  const savePostsToStorage = (posts) => {
    localStorage.setItem('boardPosts', JSON.stringify(posts));
  };

  useEffect(() => {
    setLoading(true);
    let currentPost = location.state?.post;

    if (!currentPost) {
      const allPosts = getPostsFromStorage();
      currentPost = allPosts.find((p) => p.id === parseInt(id));
    }

    setPost(currentPost);
    setLoading(false);
  }, [id, location.state]);

  const handleLike = () => {
    const allPosts = getPostsFromStorage();
    let updatedPost = null;
    const updatedPosts = allPosts.map((p) => {
      if (p.id === post.id) {
        updatedPost = {
          ...p,
          isLiked: !p.isLiked,
          likeCount: p.isLiked ? p.likeCount - 1 : p.likeCount + 1,
        };
        return updatedPost;
      }
      return p;
    });
    savePostsToStorage(updatedPosts);
    setPost(updatedPost);
  };

  const handleBookmark = () => {
    const allPosts = getPostsFromStorage();
    let updatedPost = null;
    const updatedPosts = allPosts.map((p) => {
      if (p.id === post.id) {
        updatedPost = { ...p, isBookmarked: !p.isBookmarked };
        return updatedPost;
      }
      return p;
    });
    savePostsToStorage(updatedPosts);
    setPost(updatedPost);
  };

  const handleDelete = () => {
    if (window.confirm('게시글을 정말 삭제하시겠습니까?')) {
      const allPosts = getPostsFromStorage();
      const updatedPosts = allPosts.filter((p) => p.id !== post.id);
      savePostsToStorage(updatedPosts);
      navigate('/board');
    }
  };

  const handleUpdate = () => {
    setShowMenu(false);
    const newTitle = prompt('수정할 제목을 입력하세요:', post.title);
    if (newTitle === null || newTitle.trim() === '') return;
    const newContent = prompt('수정할 내용을 입력하세요:', post.content);
    if (newContent === null) return;

    const allPosts = getPostsFromStorage();
    let updatedPost = null;
    const updatedPosts = allPosts.map((p) => {
      if (p.id === post.id) {
        updatedPost = { ...p, title: newTitle, content: newContent };
        return updatedPost;
      }
      return p;
    });
    savePostsToStorage(updatedPosts);
    setPost(updatedPost);
  };

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
    if (e.key === 'Enter' && e.ctrlKey) handleAddComment();
  };

  if (loading) {
    return (
      <div className={styles.container}>
        <p className={styles.notFound}>게시글을 불러오는 중...</p>
      </div>
    );
  }

  if (!post) {
    return (
      <div className={styles.container}>
        <p className={styles.notFound}>게시글을 찾을 수 없습니다.</p>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <article className={styles.postDetail}>
        <div className={styles.titleWrapper}>
          <h1 className={styles.title}>{post.title}</h1>
          <div className={styles.menuContainer}>
            <button
              className={styles.menuButton}
              onClick={() => setShowMenu(!showMenu)}
            >
              ⋮
            </button>
            {showMenu && (
              <div className={styles.menuDropdown}>
                <button onClick={handleUpdate}>
                  <img
                    src={EditIcon}
                    className={styles.EditIcon}
                    alt="수정버튼"
                  />
                  수정하기
                </button>
                <button onClick={handleDelete}>
                  <img
                    src={DeleteIcon}
                    className={styles.DeleteIcon}
                    alt="삭제버튼"
                  />
                  삭제하기
                </button>
              </div>
            )}
          </div>
        </div>

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
          <div className={styles.commentsHeaderWrapper}>
            <div className={styles.actions}>
              <button
                className={styles.actionButton}
                onClick={handleBookmark}
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
                onClick={handleLike}
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
            <div className={styles.commentsHeader}>
              <p className={styles.commentCount}>
                댓글 <span>{comments.length}</span>
              </p>
            </div>
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
