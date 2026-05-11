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

import {
  isDataImageUrl,
  toAbsoluteImageUrl,
  dataUrlToFile,
} from '../utils/imageUtils';

// dataUrlToFile imported from utils

const replaceBase64ImagesWithUploadedUrls = async (contentJson, uploadImage) => {
  const uploadedMediaIds = [];

  const processNode = async (node) => {
    if (!node || typeof node !== 'object') return node;

    const nextNode = { ...node };

    if (nextNode.type === 'image') {
      const src = String(nextNode.attrs?.src || '');

      if (isDataImageUrl(src)) {
        const altText = String(nextNode.attrs?.alt || '').trim();
        const file = await dataUrlToFile(src, altText || `pasted-image-${Date.now()}.png`);
        const uploaded = await uploadImage(file);
        const normalizedImageUrl = toAbsoluteImageUrl(uploaded?.url);

        nextNode.attrs = {
          ...(nextNode.attrs || {}),
          src: normalizedImageUrl || src,
          alt: uploaded?.originalFilename || altText || file.name,
        };

        if (uploaded?.mediaId) {
          uploadedMediaIds.push(uploaded.mediaId);
        }
      }
    }

    if (Array.isArray(nextNode.content)) {
      nextNode.content = await Promise.all(nextNode.content.map((childNode) => processNode(childNode)));
    }

    return nextNode;
  };

  const normalizedContentJson = await processNode(contentJson);

  return {
    normalizedContentJson,
    uploadedMediaIds,
  };
};

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
      const width = String(node.attrs?.width || '').trim();
      const height = String(node.attrs?.height || '').trim();
      const align = String(node.attrs?.align || 'left').trim();
      const alignStyle = align === 'center'
        ? 'display: block; margin-left: auto; margin-right: auto;'
        : align === 'right'
          ? 'display: block; margin-left: auto; margin-right: 0;'
          : 'display: block; margin-left: 0; margin-right: auto;';
      const style = ` style="${alignStyle}${width ? ` width: ${width};` : ''}${height ? ` height: ${height};` : ' height: auto;'}"`;
      const widthAttr = width ? ` width="${width.replace(/"/g, '&quot;')}"` : '';
      const heightAttr = height ? ` height="${height.replace(/"/g, '&quot;')}"` : '';
      const alignAttr = ` data-align="${align}"`;
      return `<img src="${src}" alt="${alt}"${widthAttr}${heightAttr}${alignAttr}${style} />`;
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
  const [selectedParentId, setSelectedParentId] = useState('');
  const [subBoardOptions, setSubBoardOptions] = useState([]);
  const [selectedSubBoardId, setSelectedSubBoardId] = useState('');
  const [idToSegment, setIdToSegment] = useState({});
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
        const idSegmentMap = {};

        (boards || []).forEach((board) => {
          const boardName = String(board.boardName || '').trim();
          const segment = toBoardRouteSegment(boardName);
          if (!segment) return;

          idMap[segment] = board.boardId;
          nameMap[segment] = boardName;
          options.push({ id: board.boardId, name: boardName, segment });
          idSegmentMap[board.boardId] = segment;
        });

        setBoardIdMap(idMap);
        setBoardNameMap(nameMap);
        setBoardOptions(options);
        setIdToSegment((prev) => ({ ...prev, ...idSegmentMap }));
        const initialBoardId = currentBoardId || location.state?.boardId || options[0]?.id || '';
        setSelectedParentId(initialBoardId);
        // if location.state.boardId is a child id we will select it after loading children
      } catch (error) {
        console.error('게시판 목록 로드 실패:', error);
      }
    };

    loadBoards();
  }, [currentBoardId, location.state?.boardId]);

  // load sub-boards when parent selection changes
  useEffect(() => {
    if (!selectedParentId) {
      setSubBoardOptions([]);
      setSelectedSubBoardId('');
      return;
    }

    let mounted = true;
    (async () => {
      try {
        const subs = await boardApi.getSubBoards(selectedParentId);
        if (!mounted) return;
        const subOptions = (subs || []).map((b) => {
          const name = String(b.boardName || '').trim();
          const segment = toBoardRouteSegment(name);
          return { id: b.boardId, name, segment, parentBoardId: b.parentBoardId };
        });

        setSubBoardOptions(subOptions);

        // map id -> segment for navigation
        const idSeg = {};
        subOptions.forEach((s) => (idSeg[s.id] = s.segment));
        setIdToSegment((prev) => ({ ...prev, ...idSeg }));

        // If user navigated here with a specific boardId in state, prefer that
        const incomingBoardId = location.state?.boardId;
        if (incomingBoardId && subOptions.some((s) => s.id === incomingBoardId)) {
          setSelectedSubBoardId(incomingBoardId);
          return;
        }

        // default: clear child selection
        setSelectedSubBoardId('');
      } catch (err) {
        console.error('하위 게시판 로드 실패:', err);
        setSubBoardOptions([]);
        setSelectedSubBoardId('');
      }
    })();

    return () => {
      mounted = false;
    };
  }, [selectedParentId, location.state?.boardId]);

  const computedSelectedBoardId = useMemo(() => selectedSubBoardId || selectedParentId || '', [selectedParentId, selectedSubBoardId]);

  const currentBoardPath = useMemo(() => {
    // When a sub-board is selected, navigate to the parent board route and include subBoardId query
    if (!computedSelectedBoardId) return '/board';

    if (selectedSubBoardId) {
      const sub = subBoardOptions.find((s) => s.id === selectedSubBoardId);
      const parentId = sub?.parentBoardId || selectedParentId;
      const parentSegment = idToSegment[parentId] || idToSegment[selectedParentId];
      const base = parentSegment && parentSegment !== 'root' ? `/board/${encodeURIComponent(parentSegment)}` : '/board';
      return `${base}${selectedSubBoardId ? `?subBoardId=${encodeURIComponent(selectedSubBoardId)}` : ''}`;
    }

    const segment = idToSegment[computedSelectedBoardId];
    if (!segment || segment === 'root') return '/board';
    return `/board/${encodeURIComponent(segment)}`;
  }, [computedSelectedBoardId, idToSegment, selectedSubBoardId, selectedParentId, subBoardOptions]);

  const handleBack = () => {
    navigate(currentBoardPath);
  };

  const handleBoardChange = (boardId) => {
    // when user changes the top-level select, treat it as parent selection and clear child
    setSelectedParentId(boardId);
    setSelectedSubBoardId('');
  };

  const handleSubBoardChange = (boardId) => {
    setSelectedSubBoardId(boardId);
  };

  const handleSave = async () => {
    if (isSaving) return;

    if (!computedSelectedBoardId) {
      alert('게시판을 선택해주세요.');
      return;
    }

    const normalizedTitle = title.trim();
    if (!normalizedTitle) {
      alert('제목을 입력해주세요.');
      return;
    }

    let normalizedContentJson = contentJson;
    let normalizedInlineMediaIds = inlineMediaIds;

    try {
      const { normalizedContentJson: replacedContentJson, uploadedMediaIds } = await replaceBase64ImagesWithUploadedUrls(
        contentJson,
        boardApi.uploadBoardImage
      );
      normalizedContentJson = replacedContentJson;
      normalizedInlineMediaIds = Array.from(new Set([...(inlineMediaIds || []), ...(uploadedMediaIds || [])].filter(Boolean)));
    } catch (error) {
      console.error('본문 이미지 정규화 실패:', error);
      alert('본문 이미지 처리에 실패했습니다. 다시 시도해주세요.');
      return;
    }

    const contentHtml = jsonToHtml(normalizedContentJson);
    const contentText = getTextFromJson(normalizedContentJson);

    if (!contentText) {
      alert('내용을 입력해주세요.');
      return;
    }

    try {
      setIsSaving(true);
      const payload = {
        boardId: computedSelectedBoardId,
        title: normalizedTitle,
        contentFormat: 'TIPTAP_JSON',
        contentJson: normalizedContentJson,
        contentHtml,
        contentText,
        anonymous: isAnonymous,
        inlineMediaIds: normalizedInlineMediaIds,
        attachmentIds: attachmentFiles.map((file) => file.mediaId).filter(Boolean),
      };

      await boardApi.createRichPost(payload);
      setContentJson(normalizedContentJson);
      setInlineMediaIds(normalizedInlineMediaIds);
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
            {Array.isArray(subBoardOptions) && subBoardOptions.length > 0 ? (
              <div>
                <label className={styles.label}>게시판 선택</label>
                <select className={styles.select} value={selectedSubBoardId} onChange={(e) => handleSubBoardChange(e.target.value)}>
                  <option value="">(선택) 게시판 선택</option>
                  {subBoardOptions.map((board) => (
                    <option key={board.id} value={board.id}>
                      {board.name}
                    </option>
                  ))}
                </select>
              </div>
            ) : null}
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
              <label className={styles.fileButton}>
                첨부파일 추가
                <input type="file" multiple className={styles.hiddenFileInput} onChange={handleAttachmentChange} />
              </label>
            </div>

            <RichTextEditor
              value={contentJson}
              onChange={setContentJson}
              placeholder="내용을 입력해 주세요."
              onUploadImage={boardApi.uploadBoardImage}
              onImageInserted={(media) => {
                if (media?.mediaId) {
                  setInlineMediaIds((prev) => Array.from(new Set([...(prev || []), media.mediaId])));
                }
              }}
            />
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
