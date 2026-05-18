// components/Board/PostDetail.jsx
import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import * as boardApi from '../utils/boardApi';
import { api } from '../utils/axios';
import styles from './PostDetail.module.css';
import { toBoardRouteSegment } from '../utils/boardRoute';

import PostView from '../components/Board/PostDetail/PostView';
import PostEditForm from '../components/Board/PostDetail/PostEditForm';
import CommentSection from '../components/Board/PostDetail/CommentSection';

const FILE_DOWNLOAD_BASE_URL = (import.meta.env.VITE_API_URL || '').replace(
  /\/$/,
  ''
);

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
  if (!data?.comments) return [];
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
      comment.replies.forEach((reply) => flatComments.push(reply));
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

const PostDetail = () => {
  const { postId, team } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  // 상태 관리
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [isEdit, setIsEdit] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editFiles, setEditFiles] = useState([]);
  const [newFiles, setNewFiles] = useState([]);
  const [isSavingEdit, setIsSavingEdit] = useState(false);

  const [comments, setComments] = useState([]);
  const [showMenu, setShowMenu] = useState(false);
  const [showCommentMenu, setShowCommentMenu] = useState(null);
  const [replyTargetId, setReplyTargetId] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);

  useEffect(() => {
    if (!showMenu && !showCommentMenu) return;

    const handleOutsideClick = (event) => {
      const target = event.target;
      if (!(target instanceof Element)) return;

      if (!target.closest(`.${styles.menuContainer}`)) {
        setShowMenu(false);
        setShowCommentMenu(null);
      }
    };

    document.addEventListener('mousedown', handleOutsideClick);
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
    };
  }, [showMenu, showCommentMenu]);

  const boardName =
    post?.boardName || post?.board?.boardName || post?.board?.name || '';
  const boardId = post?.boardId || post?.board?.boardId || '';

  const postAuthorId = post?.user?.id || post?.userId || post?.createdBy?.id;
  const currentUserId = currentUser?.id || currentUser?.userId;

  const isPostOwner = Boolean(
    post &&
      currentUser &&
      postAuthorId &&
      currentUserId &&
      postAuthorId === currentUserId
  );

  // 데이터 로드 로직
  const refreshPostAndComments = async () => {
    try {
      const updatedPost = await boardApi.getPost(postId);
      setPost(updatedPost);
      setEditTitle(updatedPost.title);
      setEditContent(updatedPost.contentJson || updatedPost.content || updatedPost.contentText || '');
      const raw = extractRawComments(updatedPost);
      setComments(buildCommentTree(raw));
      return updatedPost;
    } catch (error) {
      console.error('데이터 갱신 실패:', error);
      return null;
    }
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
        try { console.log('fetched post data:', data); } catch (e) {}
        setPost(data);
        setEditTitle(data.title);
        setEditContent(data.contentJson || data.content || data.contentText || '');
        const raw = extractRawComments(data);
        setComments(buildCommentTree(raw));
        setError(null);
      } catch (error) {
        console.error('게시글 불러오기 실패:', error);
        setError('게시글을 불러올 수 없습니다.');
        setComments([]);
      } finally {
        setLoading(false);
      }
    };
    fetchPostAndComments();
  }, [postId]);

  useEffect(() => {
    const fetchCurrentUser = async () => {
      try {
        const { data } = await api.get('/api/user/details');
        setCurrentUser(data || null);
      } catch {
        setCurrentUser(null);
      }
    };

    fetchCurrentUser();
  }, []);

  // --- 게시글 액션 핸들러 ---
  const handleLike = async () => {
    const prevPost = post;
    setPost({
      ...post,
      isLiked: !post.isLiked,
      likeCount: post.isLiked ? post.likeCount - 1 : post.likeCount + 1,
    });
    try {
      await boardApi.toggleLike(postId);
    } catch (error) {
      console.error('좋아요 처리 실패:', error);
      setPost(prevPost);
      alert('좋아요 처리에 실패했습니다.');
    }
  };

  const handleBookmark = async () => {
    const prevPost = post;
    setPost({
      ...post,
      isBookmarked: !post.isBookmarked,
      bookmarkCount: post.isBookmarked
        ? (post.bookmarkCount || 1) - 1
        : (post.bookmarkCount || 0) + 1,
    });
    try {
      await boardApi.toggleBookmark(postId);
    } catch (error) {
      console.error('북마크 처리 실패:', error);
      setPost(prevPost);
      alert('북마크 처리에 실패했습니다.');
    }
  };

  const handleAttachmentDownload = async (file) => {
    try {
      const serverFileName = file?.savedFilename;

      if (!serverFileName) {
        alert('다운로드할 savedFilename을 찾을 수 없습니다.');
        return;
      }

      const downloadUrl = `${FILE_DOWNLOAD_BASE_URL}/uploads/${encodeURIComponent(
        serverFileName
      )}`;

      const fileName =
        file?.originalFilename ||
        file?.name ||
        decodeURIComponent(serverFileName || 'download');

      const a = document.createElement('a');
      a.href = downloadUrl;
      a.download = fileName;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch (error) {
      console.error('첨부파일 다운로드 실패:', error);
      alert('첨부파일 다운로드에 실패했습니다.');
    }
  };

  // --- 수정 모드 핸들러 ---
  const handleEditStart = async () => {
    const updatedPost = await refreshPostAndComments();
    if (updatedPost) {
      setEditFiles(updatedPost.attachments || []);
      setNewFiles([]);
      setIsEdit(true);
      setShowMenu(false);
    }
  };

  const handleSaveEdit = async () => {
    if (isSavingEdit) return;

    const hasContent = (content) => {
      if (!content) return false;
      if (typeof content === 'string') return !!String(content).trim();
      if (typeof content === 'object' && Array.isArray(content.content)) return content.content.length > 0;
      return true;
    };

    if (!editTitle.trim() || !hasContent(editContent)) {
      alert('제목과 내용을 입력해주세요.');
      return;
    }

    setIsSavingEdit(true);

    try {
      const boardId = post.boardId || post.board?.boardId;
      // If editContent appears to be TipTap JSON, use updateRichPost
      let usedUpdate;
      if (editContent && typeof editContent === 'object' && Array.isArray(editContent.content)) {
        const json = editContent;
        // simple conversion to HTML for servers expecting contentHtml
        const jsonToHtml = (contentJson) => {
          if (!contentJson || !Array.isArray(contentJson.content)) return '<p></p>';
          const renderNode = (node) => {
            if (!node) return '';
            if (node.type === 'text') return String(node.text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            if (node.type === 'paragraph') return `<p>${(node.content || []).map(renderNode).join('')}</p>`;
            if (node.type === 'heading') {
              const level = Math.min(Math.max(Number(node.attrs?.level || 1), 1), 6);
              return `<h${level}>${(node.content || []).map(renderNode).join('')}</h${level}>`;
            }
            if (node.type === 'image') {
              const src = String(node.attrs?.src || '').replace(/"/g, '&quot;');
              const alt = String(node.attrs?.alt || '').replace(/"/g, '&quot;');
              const width = String(node.attrs?.width || '').trim();
              const height = String(node.attrs?.height || '').trim();
              const widthAttr = width ? ` width=\"${width.replace(/\"/g, '&quot;')}\"` : '';
              const heightAttr = height ? ` height=\"${height.replace(/\"/g, '&quot;')}\"` : '';
              const style = width || height ? ` style=\"${width ? `width: ${width};` : ''}${height ? `height: ${height};` : ''}\"` : '';
              return `<img src=\"${src}\" alt=\"${alt}\"${widthAttr}${heightAttr}${style} />`;
            }
            if (Array.isArray(node.content)) return node.content.map(renderNode).join('');
            return '';
          };
          return contentJson.content.map(renderNode).join('') || '<p></p>';
        };

        const payload = {
          boardId,
          title: editTitle,
          contentFormat: 'TIPTAP_JSON',
          contentJson: json,
          contentHtml: jsonToHtml(json),
          contentText: (function getText(j) {
            if (!j || !Array.isArray(j.content)) return '';
            const parts = [];
            const walk = (nodes) => {
              nodes.forEach((node) => {
                if (!node) return;
                if (node.type === 'text' && node.text) { parts.push(node.text); return; }
                if (Array.isArray(node.content)) { walk(node.content); if (node.type === 'paragraph' || node.type === 'heading') parts.push('\n'); }
              });
            };
            walk(j.content);
            return parts.join('').replace(/\n+/g, '\n').trim();
          })(json),
          files: newFiles,
        };

        usedUpdate = await boardApi.updateRichPost(postId, payload);
      } else {
        const updateData = {
          title: editTitle,
          content: editContent,
          files: newFiles,
        };
        usedUpdate = await boardApi.updatePost(postId, boardId, updateData);
      }
      alert('게시글이 수정되었습니다.');
      setIsEdit(false);
      setNewFiles([]);
      await refreshPostAndComments();
    } catch (error) {
      console.error('게시글 수정 실패:', error);
      alert('게시글 수정에 실패했습니다.');
    } finally {
      setIsSavingEdit(false);
    }
  };

  const handleFileHandlers = {
    add: (e) => setNewFiles((prev) => [...prev, ...Array.from(e.target.files)]),
    removeNew: (idx) => setNewFiles((prev) => prev.filter((_, i) => i !== idx)),
    removeExisting: (id) =>
      setEditFiles((prev) => prev.filter((f) => f.postAttachmentId !== id)),
  };

  const handleDelete = async () => {
    if (!window.confirm('게시글을 정말 삭제하시겠습니까?')) return;
    try {
      await boardApi.deletePost(postId);
      alert('게시글이 삭제되었습니다.');
      navigate(team ? `/board/${team}` : '/board');
    } catch (error) {
      console.error('게시글 삭제 실패:', error);
      alert('게시글 삭제에 실패했습니다.');
    }
  };

  const handleMoveToBoard = () => {
    const targetTeamSegment =
      location.state?.originTeam || team || toBoardRouteSegment(boardName);
    const targetBoardId = location.state?.originBoardId || boardId;

    if (!targetTeamSegment) {
      navigate('/board');
      return;
    }

    const query = targetBoardId
      ? `?subBoardId=${encodeURIComponent(targetBoardId)}`
      : '';

    if (targetTeamSegment === 'root') {
      navigate(`/board${query}`);
      return;
    }

    navigate(`/board/${encodeURIComponent(targetTeamSegment)}${query}`);
  };

  // --- 댓글 핸들러 ---
  const handleCommentHandlers = {
    add: async (text, anonymous) => {
      try {
        await boardApi.createComment({ postId, content: text, anonymous });
        await refreshPostAndComments();
      } catch (error) {
        console.error('댓글 작성 실패:', error);
        alert('댓글 작성에 실패했습니다.');
      }
    },
    update: async (id) => {
      setShowCommentMenu(null);
      const target = findCommentInTree(comments, id);
      if (!target) return;

      const newText = prompt(
        '수정할 내용을 입력하세요:',
        target.content || target.text
      );
      if (!newText || !newText.trim()) return;

      try {
        await boardApi.updateComment(id, {
          postId,
          content: newText,
          parentCommentId: target.parentCommentId,
        });
        await refreshPostAndComments();
      } catch (error) {
        console.error('댓글 수정 실패:', error);
        alert('댓글 수정에 실패했습니다.');
      }
    },
    delete: async (id) => {
      if (!window.confirm('댓글을 정말 삭제하시겠습니까?')) return;
      try {
        await boardApi.deleteComment(id);
        setShowCommentMenu(null);
        await refreshPostAndComments();
      } catch (error) {
        console.error('댓글 삭제 실패:', error);
        alert('댓글 삭제에 실패했습니다.');
      }
    },
    reply: async (parentId, text, anonymous) => {
      try {
        await boardApi.createComment({
          postId,
          content: text,
          parentCommentId: parentId,
          anonymous,
        });
        await refreshPostAndComments();
        setReplyTargetId(null);
      } catch (error) {
        console.error('답글 작성 실패:', error);
        alert('답글 작성에 실패했습니다.');
      }
    },
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
        {isEdit ? (
          <PostEditForm
            title={editTitle}
            setTitle={setEditTitle}
            content={editContent}
            setContent={setEditContent}
            editFiles={editFiles}
            newFiles={newFiles}
            onRemoveExistingFile={handleFileHandlers.removeExisting}
            onRemoveNewFile={handleFileHandlers.removeNew}
            onAddNewFile={handleFileHandlers.add}
            onSave={handleSaveEdit}
            onCancel={() => setIsEdit(false)}
            isSaving={isSavingEdit}
          />
        ) : (
          <PostView
            post={post}
            boardName={boardName}
            canManagePost={isPostOwner}
            showMenu={showMenu}
            setShowMenu={setShowMenu}
            onEdit={handleEditStart}
            onDelete={handleDelete}
            onDownload={handleAttachmentDownload}
            onMoveToBoard={handleMoveToBoard}
          />
        )}

        {!isEdit && (
          <CommentSection
            post={post}
            comments={comments}
            onLike={handleLike}
            onBookmark={handleBookmark}
            onAddComment={handleCommentHandlers.add}
            onUpdateComment={handleCommentHandlers.update}
            onDeleteComment={handleCommentHandlers.delete}
            onSubmitReply={handleCommentHandlers.reply}
            onReplyClick={(id) =>
              setReplyTargetId(id === replyTargetId ? null : id)
            }
            replyTargetId={replyTargetId}
            showCommentMenu={showCommentMenu}
            setShowCommentMenu={setShowCommentMenu}
          />
        )}
      </article>
    </div>
  );
};

export default PostDetail;
