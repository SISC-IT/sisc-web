import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import * as boardApi from '../utils/boardApi';
import { normalizeBoardRouteSegment, toBoardRouteSegment } from '../utils/boardRoute';
import RichTextEditor from '../components/Board/RichTextEditor';
import styles from './BoardWrite.module.css';

const createEmptyDoc = () => ({
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [{ type: 'text', text: '' }],
    },
  ],
});

const getTextFromJson = (contentJson) => {
  if (!contentJson || !Array.isArray(contentJson.content)) return '';

  const parts = [];
  const walk = (nodes) => {
    nodes.forEach((node) => {
      if (!node) return;
      if (node.type === 'text' && node.text) {
        parts.push(node.text);
        return;
      }
      if (Array.isArray(node.content)) {
        walk(node.content);
        if (node.type === 'paragraph' || node.type === 'heading') {
          parts.push('\n');
        }
      }
    });
  };

  walk(contentJson.content);
  return parts.join('').replace(/\n+/g, '\n').trim();
};

const jsonToHtml = (contentJson) => {
  if (!contentJson || !Array.isArray(contentJson.content)) {
    return '<p></p>';
  }

  const renderNode = (node) => {
    if (!node) return '';
    if (node.type === 'text') {
      return String(node.text || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
    }
    if (node.type === 'paragraph') {
      return `<p>${(node.content || []).map(renderNode).join('')}</p>`;
    }
    if (node.type === 'heading') {
      const level = Math.min(Math.max(Number(node.attrs?.level || 1), 1), 6);
      return `<h${level}>${(node.content || []).map(renderNode).join('')}</h${level}>`;
    }
    if (node.type === 'image') {
      const src = String(node.attrs?.src || '').replace(/"/g, '&quot;');
      const alt = String(node.attrs?.alt || '').replace(/"/g, '&quot;');
      return `<img src="${src}" alt="${alt}" />`;
    }
    if (Array.isArray(node.content)) {
      return node.content.map(renderNode).join('');
    }
    return '';
  };

  return contentJson.content.map(renderNode).join('') || '<p></p>';
};

const BoardWrite = () => {
  const { team } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [boardIdMap, setBoardIdMap] = useState({});
  const [boardNameMap, setBoardNameMap] = useState({});
  const [boardOptions, setBoardOptions] = useState([]);
  const [selectedBoardId, setSelectedBoardId] = useState('');
  const [title, setTitle] = useState('');
  const [contentJson, setContentJson] = useState(createEmptyDoc());
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [attachmentFiles, setAttachmentFiles] = useState([]);
  const [inlineMediaIds, setInlineMediaIds] = useState([]);

  const currentSegment = useMemo(() => {
    return team ? normalizeBoardRouteSegment(team) || 'root' : 'root';
  }, [team]);

  const currentBoardId = boardIdMap[currentSegment];
  const currentBoardName = boardNameMap[currentSegment] || '게시판';

  useEffect(() => {
    const loadBoards = async () => {
      try {
        const boards = await boardApi.getParentBoards();
        const idMap = {};
        const nameMap = {};
        const options = [];

        (boards || []).forEach((board) => {
          const boardName = String(board.boardName || '').trim();
          const segment = toBoardRouteSegment(boardName);
          if (!segment) return;

          idMap[segment] = board.boardId;
          nameMap[segment] = boardName;
          options.push({ id: board.boardId, name: boardName, segment });
        });

        setBoardIdMap(idMap);
        setBoardNameMap(nameMap);
        setBoardOptions(options);

        const initialBoardId = currentBoardId || location.state?.boardId || options[0]?.id || '';
        setSelectedBoardId(initialBoardId);
      } catch (error) {
        console.error('게시판 목록 로드 실패:', error);
      }
    };

    loadBoards();
  }, [currentBoardId, location.state?.boardId]);

  const currentBoardPath = useMemo(() => {
    if (!selectedBoardId) return '/board';
    const selectedBoard = boardOptions.find((board) => board.id === selectedBoardId);
    if (!selectedBoard) return '/board';
    if (selectedBoard.segment === 'root') return '/board';
    return `/board/${encodeURIComponent(selectedBoard.segment)}`;
  }, [boardOptions, selectedBoardId]);

  const handleBack = () => {
    navigate(currentBoardPath);
  };

  const handleBoardChange = (boardId) => {
    setSelectedBoardId(boardId);
  };

  const handleSave = async () => {
    if (isSaving) return;

    if (!selectedBoardId) {
      alert('게시판을 선택해주세요.');
      return;
    }

    const normalizedTitle = title.trim();
    if (!normalizedTitle) {
      alert('제목을 입력해주세요.');
      return;
    }

    const contentHtml = jsonToHtml(contentJson);
    const contentText = getTextFromJson(contentJson);

    if (!contentText) {
      alert('내용을 입력해주세요.');
      return;
    }

    try {
      setIsSaving(true);
      const payload = {
        boardId: selectedBoardId,
        title: normalizedTitle,
        contentFormat: 'TIPTAP_JSON',
        contentJson,
        contentHtml,
        contentText,
        anonymous: isAnonymous,
        inlineMediaIds,
        attachmentIds: attachmentFiles.map((file) => file.mediaId).filter(Boolean),
      };

      await boardApi.createRichPost(payload);
      alert('게시글이 작성되었습니다.');
      navigate(currentBoardPath);
    } catch (error) {
      console.error('게시글 작성 실패:', error);
      alert(`게시글 작성에 실패했습니다: ${error.message || '알 수 없는 오류'}`);
    } finally {
      setIsSaving(false);
    }
  };

  const handleAttachmentChange = async (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    try {
      const uploaded = await Promise.all(
        files.map(async (file) => {
          return boardApi.uploadBoardFile(file);
        })
      );
      setAttachmentFiles((prev) => [...prev, ...uploaded]);
    } catch (error) {
      console.error('첨부파일 업로드 실패:', error);
      alert('첨부파일 업로드에 실패했습니다.');
    } finally {
      event.target.value = '';
    }
  };

  const handleImageUpload = async (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    try {
      const uploadedImages = await Promise.all(files.map((file) => boardApi.uploadBoardImage(file)));
      const nextInlineMediaIds = uploadedImages.map((image) => image.mediaId).filter(Boolean);
      setInlineMediaIds((prev) => [...prev, ...nextInlineMediaIds]);

      setContentJson((prev) => ({
        ...prev,
        content: [
          ...(Array.isArray(prev?.content) ? prev.content : []),
          ...uploadedImages.map((image) => ({
            type: 'image',
            attrs: {
              src: image.url,
              alt: image.originalFilename || '',
            },
          })),
        ],
      }));
    } catch (error) {
      console.error('본문 이미지 업로드 실패:', error);
      alert('본문 이미지 업로드에 실패했습니다.');
    } finally {
      event.target.value = '';
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <div className={styles.headerRow}>
          <button type="button" className={styles.backButton} onClick={handleBack}>
            ← 목록으로
          </button>
          <div>
            <p className={styles.kicker}>새 글 작성</p>
            <h1 className={styles.title}>{currentBoardName} 글 작성하기</h1>
          </div>
        </div>

        <div className={styles.card}>
          <div className={styles.fieldGroup}>
            <label className={styles.label}>게시판</label>
            <select className={styles.select} value={selectedBoardId} onChange={(e) => handleBoardChange(e.target.value)}>
              <option value="">게시판을 선택해 주세요.</option>
              {boardOptions.map((board) => (
                <option key={board.id} value={board.id}>
                  {board.name}
                </option>
              ))}
            </select>
          </div>

          <div className={styles.fieldGroup}>
            <label className={styles.label}>제목</label>
            <input
              className={styles.input}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="제목을 입력해 주세요."
            />
          </div>

          <div className={styles.fieldGroup}>
            <div className={styles.fieldHeader}>
              <label className={styles.label}>내용</label>
              <div className={styles.fileActions}>
                <label className={styles.fileButton}>
                  본문 이미지
                  <input type="file" accept="image/*" multiple className={styles.hiddenFileInput} onChange={handleImageUpload} />
                </label>
                <label className={styles.fileButton}>
                  첨부파일 추가
                  <input type="file" multiple className={styles.hiddenFileInput} onChange={handleAttachmentChange} />
                </label>
              </div>
            </div>

            <RichTextEditor value={contentJson} onChange={setContentJson} placeholder="내용을 입력해 주세요." />
          </div>

          {attachmentFiles.length > 0 && (
            <div className={styles.attachmentList}>
              <p className={styles.attachmentTitle}>첨부 파일</p>
              <div className={styles.attachmentGrid}>
                {attachmentFiles.map((file) => (
                  <div key={file.mediaId} className={styles.attachmentItem}>
                    <span>{file.originalFilename}</span>
                    <span className={styles.attachmentMeta}>{file.mediaType}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className={styles.footerRow}>
            <label className={styles.anonymousOption}>
              <input type="checkbox" checked={isAnonymous} onChange={(e) => setIsAnonymous(e.target.checked)} />
              익명
            </label>
            <div className={styles.actionGroup}>
              <button type="button" className={styles.secondaryButton} onClick={handleBack}>
                취소
              </button>
              <button type="button" className={styles.primaryButton} onClick={handleSave} disabled={isSaving}>
                {isSaving ? '게시글 작성 중...' : '게시글 작성'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BoardWrite;
