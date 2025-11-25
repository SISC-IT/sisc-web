import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import styles from './PostDetail.module.css';

import ProfileIcon from '../assets/board_profile.svg';
import BookmarkIcon from '../assets/boardBookMark.svg';
import BookmarkFilledIcon from '../assets/boardBookMark.fill.svg';
import HeartIcon from '../assets/boardHeart.svg';
import HeartFilledIcon from '../assets/boardHeart.fill.svg';
import EditIcon from '../assets/boardPencil.svg';
import DeleteIcon from '../assets/boardCloseIcon.svg';
import FolderIcon from '../assets/boardFolder.svg';
import { getTimeAgo } from '../utils/TimeUtils';

const PostDetail = () => {
  const { postId, team } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);

  const [isEdit, setIsEdit] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editFiles, setEditFiles] = useState([]);
  const [newFiles, setNewFiles] = useState([]);

  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState('');
  const [showMenu, setShowMenu] = useState(false);
  const [showCommentMenu, setShowCommentMenu] = useState(null);

  // 게시글 저장소 함수
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
    try {
      localStorage.setItem('boardPosts', JSON.stringify(posts));
    } catch (error) {
      if (error.name === 'QuotaExceededError') {
        console.error('localStorage 용량이 부족합니다.');
      }
      throw error;
    }
  };

  // 댓글 저장소 함수
  const getCommentsFromStorage = (postId) => {
    try {
      const saved = localStorage.getItem(`comments_${postId}`);
      if (!saved) return [];
      return JSON.parse(saved).map((c) => ({ ...c, date: new Date(c.date) }));
    } catch (error) {
      console.error('Failed to parse comments from localStorage', error);
      return [];
    }
  };

  const saveCommentsToStorage = (postId, comments) => {
    localStorage.setItem(`comments_${postId}`, JSON.stringify(comments));
  };

  // 게시글 로딩 및 댓글 로딩
  useEffect(() => {
    setLoading(true);
    let currentPost = location.state?.post;

    if (!currentPost) {
      const allPosts = getPostsFromStorage();
      currentPost = allPosts.find((p) => p.id === parseInt(postId, 10));
    }
    setPost(currentPost);
    setLoading(false);

    if (currentPost) {
      setEditTitle(currentPost.title);
      setEditContent(currentPost.content);
    }

    if (currentPost) {
      setComments(getCommentsFromStorage(currentPost.id));
    } else {
      setComments([]);
    }
  }, [postId, location.state]);

  // 좋아요 토글
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

  // 북마크 토글
  const handleBookmark = () => {
    const allPosts = getPostsFromStorage();
    let updatedPost = null;
    const updatedPosts = allPosts.map((p) => {
      if (p.id === post.id) {
        updatedPost = {
          ...p,
          isBookmarked: !p.isBookmarked,
          bookmarkCount: p.isBookmarked
            ? (p.bookmarkCount || 1) - 1
            : (p.bookmarkCount || 0) + 1,
        };
        return updatedPost;
      }
      return p;
    });
    savePostsToStorage(updatedPosts);
    setPost(updatedPost);
  };

  // 수정 모드 진입
  const handleEdit = () => {
    setIsEdit(true);
    setShowMenu(false);
    setEditFiles(post.files || []);
    setNewFiles([]);
  };

  // 수정 취소
  const handleCancelEdit = () => {
    setIsEdit(false);
    setEditTitle(post.title);
    setEditContent(post.content);
  };

  // 기존 파일 삭제
  const handleRemoveExistingFile = (index) => {
    setEditFiles(editFiles.filter((_, i) => i !== index));
  };

  // 새 파일 추가
  const handleAddNewFile = (e) => {
    const files = Array.from(e.target.files);
    setNewFiles([...newFiles, ...files]);
    e.target.value = '';
  };

  // 새 파일 삭제
  const handleRemoveNewFile = (index) => {
    setNewFiles(newFiles.filter((_, i) => i !== index));
  };

  // 수정 저장
  const handleSaveEdit = () => {
    if (!editTitle.trim() || !editContent.trim()) {
      alert('제목과 내용을 입력해주세요.');
      return;
    }

    // 기존 파일 + 새 파일 정보 합치기
    const allFiles = [
      ...editFiles,
      ...newFiles.map((file) => ({
        name: file.name,
        size: file.size,
        type: file.type,
      })),
    ];

    const allPosts = getPostsFromStorage();
    let updatedPost = null;
    const updatedPosts = allPosts.map((p) => {
      if (p.id === post.id) {
        updatedPost = {
          ...p,
          title: editTitle,
          content: editContent,
          files: allFiles,
        };
        return updatedPost;
      }
      return p;
    });
    savePostsToStorage(updatedPosts);
    setPost(updatedPost);
    setIsEdit(false);
    setNewFiles([]);
  };

  // 게시글 삭제
  const handleDelete = () => {
    if (window.confirm('게시글을 정말 삭제하시겠습니까?')) {
      const allPosts = getPostsFromStorage();
      const updatedPosts = allPosts.filter((p) => p.id !== post.id);
      savePostsToStorage(updatedPosts);
      navigate(`/board/${team || 'all'}`);
    }
  };

  // 댓글 추가
  const handleAddComment = () => {
    if (!commentText.trim()) return;
    const newComment = {
      id: Date.now(),
      author: '사용자',
      text: commentText,
      date: new Date(),
    };
    const updatedComments = [...comments, newComment];
    setComments(updatedComments);
    saveCommentsToStorage(post.id, updatedComments);
    setCommentText('');
  };

  // 댓글 수정
  const handleUpdateComment = (commentId) => {
    setShowCommentMenu(null);
    const currentComment = comments.find((c) => c.id === commentId);
    const newText = prompt('수정할 댓글을 입력하세요:', currentComment.text);
    if (newText === null || newText.trim() === '') return;
    const updatedComments = comments.map((c) =>
      c.id === commentId ? { ...c, text: newText } : c
    );
    setComments(updatedComments);
    saveCommentsToStorage(post.id, updatedComments);
  };

  // 댓글 삭제
  const handleDeleteComment = (commentId) => {
    if (window.confirm('댓글을 정말 삭제하시겠습니까?')) {
      const updatedComments = comments.filter((c) => c.id !== commentId);
      setComments(updatedComments);
      saveCommentsToStorage(post.id, updatedComments);
      setShowCommentMenu(null);
    }
  };

  // 댓글 입력창에서 ctrl+enter로 댓글 추가
  const handleKeyDown = (e) => {
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
          {isEdit ? (
            <input
              className={styles.editTitleInput}
              type="text"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              placeholder="제목을 입력하세요"
            />
          ) : (
            <h1 className={styles.title}>{post.title}</h1>
          )}

          {!isEdit && (
            <div className={styles.menuContainer}>
              <button
                className={styles.menuButton}
                onClick={() => setShowMenu(!showMenu)}
              >
                ⋮
              </button>
              {showMenu && (
                <div className={styles.menuDropdown}>
                  <button onClick={handleEdit}>
                    <img
                      src={EditIcon}
                      className={styles.EditIcon}
                      alt="수정"
                    />
                    수정하기
                  </button>
                  <button onClick={handleDelete}>
                    <img
                      src={DeleteIcon}
                      className={styles.DeleteIcon}
                      alt="삭제"
                    />
                    삭제하기
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        <div className={styles.divider} />
        <div className={styles.meta}>
          <img src={ProfileIcon} className={styles.profileIcon} alt="프로필" />
          <div className={styles.metaInfo}>
            <p className={styles.author}>운영진</p>
            <p className={styles.date}>{getTimeAgo(post.date)}</p>
          </div>
        </div>

        {isEdit ? (
          <textarea
            className={styles.editContentTextarea}
            value={editContent}
            onChange={(e) => setEditContent(e.target.value)}
            placeholder="내용을 입력하세요"
            rows={10}
          />
        ) : (
          <div className={styles.content}>{post.content}</div>
        )}

        {/* 수정 모드: 항상 표시 / 일반 모드: 파일 있을 때만 표시 */}
        {(isEdit || (post.files && post.files.length > 0)) && (
          <div className={styles.attachments}>
            <h3 className={styles.attachmentTitle}>
              첨부 파일 (
              {isEdit
                ? editFiles.length + newFiles.length
                : post.files?.length || 0}
              )
            </h3>

            {/* 기존 파일 목록 */}
            {isEdit && editFiles.length > 0 && (
              <div className={styles.attachmentList}>
                {editFiles.map((file, index) => (
                  <div
                    key={`existing-${file.name}-${file.size}`}
                    className={styles.attachmentItem}
                  >
                    <img
                      src={FolderIcon}
                      alt="파일"
                      className={styles.attachmentIcon}
                    />
                    <span className={styles.attachmentName}>{file.name}</span>
                    <span className={styles.attachmentSize}>
                      ({(file.size / 1024).toFixed(1)} KB)
                    </span>
                    <button
                      className={styles.removeFileButton}
                      onClick={() => handleRemoveExistingFile(index)}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}

            {/* 새로 추가된 파일 목록 */}
            {isEdit && newFiles.length > 0 && (
              <div className={styles.attachmentList}>
                {newFiles.map((file, index) => (
                  <div key={`new-${index}`} className={styles.attachmentItem}>
                    <img
                      src={FolderIcon}
                      alt="파일"
                      className={styles.attachmentIcon}
                    />
                    <span className={styles.attachmentName}>
                      {file.name}{' '}
                      <span className={styles.newBadge}>새파일</span>
                    </span>
                    <span className={styles.attachmentSize}>
                      ({(file.size / 1024).toFixed(1)} KB)
                    </span>
                    <button
                      className={styles.removeFileButton}
                      onClick={() => handleRemoveNewFile(index)}
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}

            {/* 파일 추가 버튼 */}
            {isEdit && (
              <div className={styles.fileAddSection}>
                <input
                  type="file"
                  id="editFileUpload"
                  multiple
                  onChange={handleAddNewFile}
                  style={{ display: 'none' }}
                />
                <button
                  className={styles.addFileButton}
                  onClick={() =>
                    document.getElementById('editFileUpload').click()
                  }
                >
                  파일 추가
                </button>
              </div>
            )}

            {/* 일반 모드: 파일 목록만 표시 */}
            {!isEdit && post.files && post.files.length > 0 && (
              <div className={styles.attachmentList}>
                {post.files.map((file, index) => (
                  <div key={index} className={styles.attachmentItem}>
                    <img
                      src={FolderIcon}
                      alt="파일"
                      className={styles.attachmentIcon}
                    />
                    <span className={styles.attachmentName}>{file.name}</span>
                    <span className={styles.attachmentSize}>
                      ({(file.size / 1024).toFixed(1)} KB)
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* 수정 모드 버튼 */}
        {isEdit && (
          <div className={styles.editButtons}>
            <button className={styles.saveButton} onClick={handleSaveEdit}>
              저장
            </button>
            <button className={styles.cancelButton} onClick={handleCancelEdit}>
              취소
            </button>
          </div>
        )}

        {!isEdit && (
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
                  {post.bookmarkCount > 0 && (
                    <span className={styles.count}>{post.bookmarkCount}</span>
                  )}
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
                onKeyDown={handleKeyDown}
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

                      {/* 댓글 수정/삭제 메뉴 */}
                      <div className={styles.menuContainer}>
                        <button
                          className={styles.menuButton}
                          onClick={() => setShowCommentMenu(id)}
                          aria-label="댓글 메뉴 열기"
                        >
                          ⋮
                        </button>
                        {showCommentMenu === id && (
                          <div className={styles.menuDropdown}>
                            <button onClick={() => handleUpdateComment(id)}>
                              <img
                                src={EditIcon}
                                className={styles.EditIcon}
                                alt="수정버튼"
                              />
                              수정하기
                            </button>
                            <button onClick={() => handleDeleteComment(id)}>
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
                    <p className={styles.commentText}>{text}</p>
                  </div>
                ))
              ) : (
                <p className={styles.noComments}>댓글이 없습니다.</p>
              )}
            </div>
          </div>
        )}
      </article>
    </div>
  );
};

export default PostDetail;
