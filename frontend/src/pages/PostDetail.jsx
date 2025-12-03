import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import * as boardApi from '../utils/boardApi';
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

const buildCommentTree = (flatComments) => {
  const map = new Map();
  const roots = [];

  flatComments.forEach((c) => {
    const commentId = c.commentId || c.id;
    map.set(commentId, { ...c, replies: [] });
  });

  map.forEach((comment) => {
    const parentId = comment.parentCommentId;

    if (parentId && map.has(parentId)) {
      map.get(parentId).replies.push(comment);
    } else {
      roots.push(comment);
    }
  });

  return roots;
};

const extractRawComments = (data) => {
  if (!data?.comments) {
    return [];
  }

  let topLevelComments = [];

  if (Array.isArray(data.comments)) {
    topLevelComments = data.comments;
  } else if (Array.isArray(data.comments.content)) {
    topLevelComments = data.comments.content;
  }

  const flatComments = [];

  topLevelComments.forEach((comment) => {
    flatComments.push(comment);

    if (comment.replies && Array.isArray(comment.replies)) {
      comment.replies.forEach((reply) => {
        flatComments.push(reply);
      });
    }
  });

  return flatComments;
};

const findCommentInTree = (nodes, targetId) => {
  for (const c of nodes) {
    const id = c.commentId || c.id;
    if (id === targetId) return c;
    if (c.replies && c.replies.length) {
      const found = findCommentInTree(c.replies, targetId);
      if (found) return found;
    }
  }
  return null;
};

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

  const [localReplyText, setLocalReplyText] = React.useState('');

  React.useEffect(() => {
    if (!isReplying) {
      setLocalReplyText('');
    }
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
              aria-label="댓글 메뉴 열기"
            >
              ⋮
            </button>
            {showCommentMenu === commentId && (
              <div className={styles.menuDropdown}>
                <button onClick={() => onUpdateComment(commentId)}>
                  <img
                    src={EditIcon}
                    className={styles.EditIcon}
                    alt="수정버튼"
                  />
                  수정하기
                </button>
                <button onClick={() => onDeleteComment(commentId)}>
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

const PostDetail = () => {
  const { postId, team } = useParams();
  const navigate = useNavigate();

  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [isEdit, setIsEdit] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editFiles, setEditFiles] = useState([]);
  const [newFiles, setNewFiles] = useState([]);

  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState('');

  const [showMenu, setShowMenu] = useState(false);
  const [showCommentMenu, setShowCommentMenu] = useState(null);
  const [replyTargetId, setReplyTargetId] = useState(null);

  const refreshPostAndComments = async () => {
    const updatedPost = await boardApi.getPost(postId);
    setPost(updatedPost);

    setEditTitle(updatedPost.title);
    setEditContent(updatedPost.content);

    const raw = extractRawComments(updatedPost);
    setComments(buildCommentTree(raw));

    return updatedPost;
  };

  useEffect(() => {
    const fetchPostAndComments = async () => {
      if (!postId) {
        setError('게시글 ID가 없습니다.');
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const data = await boardApi.getPost(postId);
        setPost(data);
        setEditTitle(data.title);
        setEditContent(data.content);

        const raw = extractRawComments(data);
        setComments(buildCommentTree(raw));
        setError(null);
      } catch (err) {
        console.error('게시글 불러오기 실패:', err);
        setError('게시글을 불러올 수 없습니다.');
        setComments([]);
      } finally {
        setLoading(false);
      }
    };

    fetchPostAndComments();
  }, [postId]);

  const handleLike = async () => {
    const prevPost = post;
    const optimisticIsLiked = !post.isLiked;
    const optimisticLikeCount = optimisticIsLiked
      ? post.likeCount + 1
      : post.likeCount - 1;

    setPost({
      ...post,
      isLiked: optimisticIsLiked,
      likeCount: optimisticLikeCount,
    });

    try {
      const res = await boardApi.toggleLike(postId);
      const data = res.data || res;

      setPost((current) => ({
        ...current,
        isLiked: data.isLiked ?? current.isLiked,
        likeCount: data.likeCount ?? current.likeCount,
      }));
    } catch (error) {
      console.error('좋아요 처리 실패:', error);
      setPost(prevPost);
      alert('좋아요 처리에 실패했습니다.');
    }
  };

  const handleBookmark = async () => {
    const prevPost = post;
    const optimisticIsBookmarked = !post.isBookmarked;
    const optimisticBookmarkCount = optimisticIsBookmarked
      ? (post.bookmarkCount || 0) + 1
      : (post.bookmarkCount || 1) - 1;

    setPost({
      ...post,
      isBookmarked: optimisticIsBookmarked,
      bookmarkCount: optimisticBookmarkCount,
    });

    try {
      const res = await boardApi.toggleBookmark(postId);
      const data = res.data || res;

      setPost((current) => ({
        ...current,
        isBookmarked: data.isBookmarked ?? current.isBookmarked,
        bookmarkCount: data.bookmarkCount ?? current.bookmarkCount,
      }));
    } catch (error) {
      console.error('북마크 처리 실패:', error);
      setPost(prevPost);
      alert('북마크 처리에 실패했습니다.');
    }
  };

  const handleEdit = async () => {
    await refreshPostAndComments();
    setEditFiles(post.attachments || []);
    setNewFiles([]);
    setIsEdit(true);
    setShowMenu(false);
  };

  const handleCancelEdit = () => {
    setIsEdit(false);
    setNewFiles([]);
  };

  const handleAttachmentDownload = (file) => {
    const baseUrl = import.meta.env.VITE_API_URL || '';
    const url = `${baseUrl}${file.filePath}`;

    const a = document.createElement('a');
    a.href = url;
    a.download = file.originalFilename;
    document.body.appendChild(a);
    a.click();
    a.remove();
  };

  const handleRemoveExistingFile = (id) => {
    setEditFiles((prev) => prev.filter((file) => file.postAttachmentId !== id));
  };

  const handleAddNewFile = (e) => {
    const files = Array.from(e.target.files);
    setNewFiles((prev) => [...prev, ...files]);
    e.target.value = '';
  };

  const handleRemoveNewFile = (index) => {
    setNewFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSaveEdit = async () => {
    if (!editTitle.trim() || !editContent.trim()) {
      alert('제목과 내용을 입력해주세요.');
      return;
    }

    try {
      const boardId = post.boardId || post.board?.boardId;
      if (!boardId) {
        alert('게시판 정보를 찾을 수 없습니다.');
        return;
      }

      const updateData = {
        title: editTitle,
        content: editContent,
        files: newFiles,
      };

      await boardApi.updatePost(postId, boardId, updateData);

      alert('게시글이 수정되었습니다.');
      setIsEdit(false);
      setNewFiles([]);

      await refreshPostAndComments();
    } catch (error) {
      console.error('게시글 수정 실패:', error);
      alert(
        `게시글 수정에 실패했습니다: ${
          error.response?.data?.message || error.message
        }`
      );
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('게시글을 정말 삭제하시겠습니까?')) return;
    try {
      await boardApi.deletePost(postId);
      alert('게시글이 삭제되었습니다.');
      const path = team ? `/board/${team}` : '/board';
      navigate(path);
    } catch (error) {
      console.error('게시글 삭제 실패:', error);
      alert('게시글 삭제에 실패했습니다.');
    }
  };

  const handleAddComment = async () => {
    if (!commentText.trim()) return;

    const savedText = commentText;
    setCommentText('');

    try {
      const commentData = { postId, content: savedText };
      await boardApi.createComment(commentData);
      await refreshPostAndComments();
    } catch (error) {
      console.error('댓글 작성 실패:', error);
      alert('댓글 작성에 실패했습니다.');
      setCommentText(savedText);
    }
  };

  const handleUpdateComment = async (commentId) => {
    setShowCommentMenu(null);

    const currentComment = findCommentInTree(comments, commentId);
    if (!currentComment) return;

    const newText = prompt(
      '수정할 댓글을 입력하세요:',
      currentComment.content || currentComment.text
    );
    if (newText === null || newText.trim() === '') return;

    const commentData = {
      postId: currentComment.postId || post.postId,
      content: newText,
      parentCommentId: currentComment.parentCommentId ?? null,
    };

    try {
      await boardApi.updateComment(commentId, commentData);
      await refreshPostAndComments();
    } catch (error) {
      console.error('댓글 수정 실패:', error);
      alert('댓글 수정에 실패했습니다.');
    }
  };

  const handleReplyClick = (commentId) => {
    if (replyTargetId === commentId) {
      setReplyTargetId(null);
    } else {
      setReplyTargetId(commentId);
    }
  };

  const handleSubmitReply = async (parentId, text) => {
    if (!text.trim()) return;

    try {
      const commentData = {
        postId,
        content: text,
        parentCommentId: parentId,
      };

      await boardApi.createComment(commentData);
      await refreshPostAndComments();
      setReplyTargetId(null);
    } catch (error) {
      console.error('대댓글 작성 실패:', error);
      alert('대댓글 작성에 실패했습니다.');
    }
  };

  const handleDeleteComment = async (commentId) => {
    if (!window.confirm('댓글을 정말 삭제하시겠습니까?')) return;
    try {
      await boardApi.deleteComment(commentId);
      setShowCommentMenu(null);
      await refreshPostAndComments();
    } catch (error) {
      console.error('댓글 삭제 실패:', error);
      alert('댓글 삭제에 실패했습니다.');
    }
  };

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

  if (error || !post) {
    return (
      <div className={styles.container}>
        <p className={styles.notFound}>
          {error || '게시글을 찾을 수 없습니다.'}
        </p>
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
                aria-label="게시글 메뉴 열기"
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
            <p className={styles.author}>
              {post.author ||
                post.user?.name ||
                post.createdBy?.name ||
                '운영진'}
            </p>
            <p className={styles.date}>
              {getTimeAgo(post.createdDate || post.createdAt || post.date)}
            </p>
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

        {(isEdit || (post.attachments && post.attachments.length > 0)) && (
          <div className={styles.attachments}>
            <h3 className={styles.attachmentTitle}>
              첨부 파일 (
              {isEdit
                ? editFiles.length + newFiles.length
                : post.attachments?.length || 0}
              )
            </h3>

            {isEdit && editFiles.length > 0 && (
              <div className={styles.attachmentList}>
                {editFiles.map((file) => (
                  <div
                    key={file.postAttachmentId}
                    className={styles.attachmentItem}
                  >
                    <img
                      src={FolderIcon}
                      alt="파일"
                      className={styles.attachmentIcon}
                    />
                    <span className={styles.attachmentName}>
                      {file.originalFilename}
                    </span>
                    <button
                      className={styles.removeFileButton}
                      onClick={() =>
                        handleRemoveExistingFile(file.postAttachmentId)
                      }
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}

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

            {isEdit && (
              <div className={styles.fileAddSection}>
                <input
                  type="file"
                  id="editFileUpload"
                  multiple
                  onChange={handleAddNewFile}
                  className={styles.hiddenInput}
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

            {!isEdit && post.attachments && post.attachments.length > 0 && (
              <div className={styles.attachmentList}>
                {post.attachments.map((file) => (
                  <div
                    key={file.postAttachmentId}
                    className={styles.attachmentItem}
                  >
                    <img
                      src={FolderIcon}
                      alt="파일 다운로드"
                      className={`${styles.attachmentIcon} ${styles.attachmentIconButton}`}
                      onClick={() => handleAttachmentDownload(file)}
                    />
                    <span className={styles.attachmentName}>
                      {file.originalFilename}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

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
                comments.map((comment) => (
                  <CommentItem
                    key={comment.commentId || comment.id}
                    comment={comment}
                    onReplyClick={handleReplyClick}
                    onUpdateComment={handleUpdateComment}
                    onDeleteComment={handleDeleteComment}
                    isReplying={
                      replyTargetId === (comment.commentId || comment.id)
                    }
                    onSubmitReply={handleSubmitReply}
                    showCommentMenu={showCommentMenu}
                    setShowCommentMenu={setShowCommentMenu}
                    replyTargetId={replyTargetId}
                    depth={0}
                  />
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
