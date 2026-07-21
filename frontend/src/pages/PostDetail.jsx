import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import * as boardApi from '../utils/boardApi';
import * as adminPublicApi from '../utils/adminPublicApi';
import { api } from '../utils/axios';
import styles from './PostDetail.module.css';
import { toBoardRouteSegment } from '../utils/boardRoute';

import PostView from '../components/Board/PostDetail/PostView';
import PostEditForm from '../components/Board/PostDetail/PostEditForm';
import CommentSection from '../components/Board/PostDetail/CommentSection';
import { jsonToHtml } from '../utils/richTextHtml';

const FILE_DOWNLOAD_BASE_URL = (import.meta.env.VITE_API_URL || '').replace(
  /\/$/,
  ''
);

const toAbsoluteFileUrl = (value = '') => {
  const raw = String(value || '').trim();
  if (!raw) return '';
  if (/^https?:\/\//i.test(raw)) return raw;
  if (raw.startsWith('/')) return `${FILE_DOWNLOAD_BASE_URL}${raw}`;
  return `${FILE_DOWNLOAD_BASE_URL}/${raw.replace(/^\/+/, '')}`;
};

const buildAttachmentDownloadUrl = (file) => {
  const directUrl = file?.url || file?.downloadUrl || file?.fileUrl || file?.savedUrl || file?.publicPath;
  if (directUrl) return toAbsoluteFileUrl(directUrl);

  const savedFilename = String(file?.savedFilename || '').trim();
  if (!savedFilename) return '';
  const encodedPath = savedFilename
    .split('/')
    .map((segment) => encodeURIComponent(segment))
    .join('/');
  return `${FILE_DOWNLOAD_BASE_URL}/uploads/${encodedPath}`;
};

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
  const isAdmin = ['VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN'].includes(
    currentUser?.role
  );

  // 데이터 로드 로직
  const normalizeAttachments = (source) => {
    if (!source) return [];
    // 백엔드 버전별 첨부파일 필드명 후보 확인
    const candidates = [
      source.attachments,
      source.fileAttachments,
      source.inlineImages,
      source.files,
      source.postAttachments,
      source.postAttachmentDtos,
      source.attachmentList,
      source.attachment || source.attachments || null,
    ];

    let arr = null;
    for (const c of candidates) {
      if (Array.isArray(c) && c.length > 0) {
        arr = c;
        break;
      }
    }

    if (!arr) return [];

    // 화면 렌더링용 첨부파일 공통 키 정규화
    return arr.map((it) => {
      if (!it || typeof it !== 'object') return null;
      const savedFilename = it.savedFilename || it.savedFileName || it.fileName || it.saved_name || it.file_key || it.key || it.savedname;
      const originalFilename = it.originalFilename || it.originalFileName || it.name || it.fileName || it.filename || it.original_name || it.original;
      const postAttachmentId = it.postAttachmentId || it.id || it.attachmentId || it.postAttachmentId || null;
      const mediaId = it.mediaId || it.id || it.media_id || null;
      const mediaType = it.mediaType || it.contentType || it.mimeType || it.type || '';
      const size = it.size || it.fileSize || it.fileSize || null;
      const url = it.url || it.downloadUrl || it.fileUrl || it.savedUrl || it.publicPath || null;

      // inlineImages/fileAttachments 응답 파일명 보정
      const normalizedOriginal = originalFilename || (it.originalFilename ? it.originalFilename : it.originalName || it.name);
      const normalizedSaved = savedFilename || (it.savedFilename ? it.savedFilename : it.fileName || it.savedName);

      return {
        ...it,
        savedFilename: normalizedSaved,
        originalFilename: normalizedOriginal,
        postAttachmentId,
        mediaId,
        mediaType,
        size,
        url,
      };
    }).filter(Boolean);
  };

  const extractInlineUrlsFromContent = (source) => {
    const urls = new Set();
    try {
      const push = (href) => {
        if (!href) return;
        const u = String(href || '').trim();
        if (!u) return;
        // 캐시 쿼리스트링 제거 후 첨부 비교
        urls.add(u.split('?')[0]);
      };

      const contentJson = source?.contentJson;
      if (contentJson && typeof contentJson === 'object' && Array.isArray(contentJson.content)) {
        const walk = (nodes) => {
          for (const node of nodes || []) {
            if (!node) continue;
            if (node.type === 'image' && node.attrs && node.attrs.src) push(node.attrs.src);
            if (node.type === 'text' && Array.isArray(node.marks)) {
              for (const mark of node.marks) {
                if (mark && mark.type === 'link' && mark.attrs && mark.attrs.href) push(mark.attrs.href);
              }
            }
            if (Array.isArray(node.content)) walk(node.content);
          }
        };
        walk(contentJson.content);
      }

      const html = source?.contentHtml || source?.content;
      if (html && typeof DOMParser !== 'undefined') {
        try {
          const parser = new DOMParser();
          const doc = parser.parseFromString(String(html || ''), 'text/html');
          const imgs = Array.from(doc.querySelectorAll('img'));
          imgs.forEach((img) => push(img.getAttribute('src')));
          const anchors = Array.from(doc.querySelectorAll('a'));
          anchors.forEach((a) => push(a.getAttribute('href')));
        } catch (e) {
          // 오래된 HTML 파싱 실패 시 첨부 렌더링 유지
        }
      }
    } catch (e) {
      // 본문 분석 실패 시 게시글 조회 유지
    }
    return Array.from(urls);
  };

  const getAttachmentIdentifier = (file) =>
    file?.postAttachmentId ||
    file?.mediaId ||
    file?.id ||
    file?.url ||
    file?.savedFilename ||
    file?.originalFilename ||
    file?.name ||
    '';

  const toFileArray = (input) => {
    if (!input) return [];
    if (Array.isArray(input)) return input.filter(Boolean);
    if (typeof FileList !== 'undefined' && input instanceof FileList) {
      return Array.from(input).filter(Boolean);
    }
    if (input?.target?.files) {
      return Array.from(input.target.files).filter(Boolean);
    }
    return [];
  };

  const refreshPostAndComments = async () => {
    try {
      const updatedPost = await boardApi.getPost(postId);
      // attachments 필드로 첨부파일 정규화
      const normalizedAttachments = normalizeAttachments(updatedPost);
      const inlineUrls = extractInlineUrlsFromContent(updatedPost).map((u) => String(u || '').split('?')[0]);
      // 본문 인라인 이미지/링크와 하단 첨부 중복 제거
      const filteredAttachments = (normalizedAttachments || []).filter((att) => {
        const attUrl = String(att?.url || att?.publicPath || att?.savedFilename || '').split('?')[0];
        const attSaved = String(att?.savedFilename || att?.originalFilename || '').split('?')[0];
        if (!attUrl && !attSaved) return true;
        // URL 또는 저장 파일명 기준 인라인 첨부 판단
        if (attUrl && inlineUrls.includes(attUrl)) return false;
        if (attSaved && inlineUrls.some((u) => u.endsWith(attSaved))) return false;
        return true;
      });

      const normalizedPost = { ...updatedPost, attachments: filteredAttachments };
      setPost(normalizedPost);
      setEditTitle(normalizedPost.title);
      setEditContent(normalizedPost.contentJson || normalizedPost.content || normalizedPost.contentText || '');
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
        const normalizedAttachments = normalizeAttachments(data);
        const inlineUrls = extractInlineUrlsFromContent(data).map((u) => String(u || '').split('?')[0]);
        const filteredAttachments = (normalizedAttachments || []).filter((att) => {
          const attUrl = String(att?.url || att?.publicPath || att?.savedFilename || '').split('?')[0];
          const attSaved = String(att?.savedFilename || att?.originalFilename || '').split('?')[0];
          if (!attUrl && !attSaved) return true;
          if (attUrl && inlineUrls.includes(attUrl)) return false;
          if (attSaved && inlineUrls.some((u) => u.endsWith(attSaved))) return false;
          return true;
        });
        const normalizedPost = { ...data, attachments: filteredAttachments };
        setPost(normalizedPost);
        setEditTitle(normalizedPost.title);
        setEditContent(normalizedPost.contentJson || normalizedPost.content || normalizedPost.contentText || '');
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
      const downloadUrl = buildAttachmentDownloadUrl(file);

      if (!downloadUrl) {
        alert('다운로드할 파일 경로를 찾을 수 없습니다.');
        return;
      }

      const serverFileName = file?.savedFilename;

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
      // 수정 폼 첨부 목록도 본문 인라인 파일 제외
      const normalized = normalizeAttachments(updatedPost) || [];
      const inlineUrls = extractInlineUrlsFromContent(updatedPost).map((u) => String(u || '').split('?')[0]);
      const filtered = normalized.filter((att) => {
        const attUrl = String(att?.url || att?.publicPath || att?.savedFilename || '').split('?')[0];
        const attSaved = String(att?.savedFilename || att?.originalFilename || '').split('?')[0];
        if (!attUrl && !attSaved) return true;
        if (attUrl && inlineUrls.includes(attUrl)) return false;
        if (attSaved && inlineUrls.some((u) => u.endsWith(attSaved))) return false;
        return true;
      });
      setEditFiles(filtered || []);
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
      // TipTap JSON 본문은 리치 게시글 수정 API 사용
      let usedUpdate;
      if (editContent && typeof editContent === 'object' && Array.isArray(editContent.content)) {
        const json = editContent;
        // 새 File 객체 선업로드
        let uploadedNewMediaIds = [];
        if (newFiles && newFiles.length > 0) {
          try {
            const uploaded = await Promise.all(newFiles.map((f) => boardApi.uploadBoardFile(f)));
            const normalizedUploadedIds = uploaded.map((u) => getAttachmentIdentifier(u)).filter(Boolean);
            if (normalizedUploadedIds.length !== uploaded.length) {
              throw new Error('첨부파일 업로드 응답에서 식별자를 찾을 수 없습니다.');
            }
            uploadedNewMediaIds = normalizedUploadedIds;
            console.log('uploaded new files for edit:', uploaded);
          } catch (err) {
            console.error('새 첨부파일 업로드 실패:', err);
            alert('첨부파일 업로드에 실패했습니다. 다시 시도해주세요.');
            setIsSavingEdit(false);
            return;
          }
        }

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
          // 리치 수정 API용 PostMedia ID만 전달
          attachmentIds: Array.from(new Set([
            ...(editFiles || []).map((f) => f?.mediaId).filter(Boolean),
            ...(uploadedNewMediaIds || []),
          ].filter(Boolean))),
        };

        usedUpdate = await boardApi.updateRichPost(postId, payload);
      } else {
        const updateData = {
          title: editTitle,
          content: editContent,
          files: newFiles,
          existingAttachmentIds: (editFiles || []).map((f) => f?.postAttachmentId).filter(Boolean),
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
    add: (input) => {
      const files = toFileArray(input);
      if (files.length === 0) return [];
      setNewFiles((prev) => [...prev, ...files]);
      if (input?.target) {
        input.target.value = '';
      }
      return files;
    },
    removeNew: (idx) => setNewFiles((prev) => prev.filter((_, i) => i !== idx)),
    removeExisting: (id) =>
      setEditFiles((prev) => prev.filter((f) => getAttachmentIdentifier(f) !== id)),
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

  const handlePublishToPublic = async () => {
    if (!window.confirm('이 게시글을 월간 세투연 외부 페이지에 공개할까요?')) return;
    try {
      await adminPublicApi.updatePublicPost(postId, { publicVisible: true });
      await refreshPostAndComments();
      setShowMenu(false);
      alert('월간 세투연에 공개되었습니다.');
    } catch (error) {
      console.error('외부 공개 전환 실패:', error);
      alert(error?.message || '외부 공개 전환에 실패했습니다.');
    }
  };

  const handleUnpublishFromPublic = async () => {
    if (!window.confirm('이 게시글을 월간 세투연 외부 페이지에서 내릴까요?')) return;
    try {
      await adminPublicApi.updatePublicPost(postId, { publicVisible: false });
      await refreshPostAndComments();
      setShowMenu(false);
      alert('월간 세투연에서 비공개 처리되었습니다.');
    } catch (error) {
      console.error('외부 비공개 전환 실패:', error);
      alert(error?.message || '외부 비공개 전환에 실패했습니다.');
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
            canManagePublic={isAdmin}
            showMenu={showMenu}
            setShowMenu={setShowMenu}
            onEdit={handleEditStart}
            onDelete={handleDelete}
            onPublishToPublic={handlePublishToPublic}
            onUnpublishFromPublic={handleUnpublishFromPublic}
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
