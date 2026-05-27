import { useEffect, useMemo, useState } from 'react';
import AdminHeader from '../components/AdminHome/AdminHeader';
import AdminSidebar from '../components/AdminHome/AdminSidebar';
import RichTextEditor from '../components/Board/RichTextEditor';
import * as boardApi from '../utils/boardApi';
import { getPublicPage, savePublicPage } from '../utils/adminPublicApi';
import {
  dataUrlToFile,
  isDataImageUrl,
  toAbsoluteImageUrl,
} from '../utils/imageUtils';
import { jsonToHtml } from '../utils/richTextHtml';
import styles from './AdminPublicPages.module.css';

const pageOptions = [
  {
    type: 'CLUB',
    title: '동아리 소개',
    description: '외부 사이트의 동아리 소개 페이지입니다.',
  },
  {
    type: 'EXECUTIVES',
    title: '임원 소개',
    description: '외부 사이트의 임원진 소개 페이지입니다.',
  },
];

const createEmptyDoc = () => ({
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [{ type: 'text', text: '' }],
    },
  ],
});

const createDocFromText = (text = '') => ({
  type: 'doc',
  content: [
    {
      type: 'paragraph',
      content: [{ type: 'text', text: text || '' }],
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

const containsImageNode = (doc) => {
  if (!doc || !Array.isArray(doc.content)) return false;
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (!node) continue;
      if (node.type === 'image') return true;
      if (Array.isArray(node.content) && walk(node.content)) return true;
    }
    return false;
  };
  return walk(doc.content);
};

const replaceBase64ImagesWithUploadedUrls = async (contentJson) => {
  const processNode = async (node) => {
    if (!node || typeof node !== 'object') return node;
    const nextNode = { ...node };

    if (nextNode.type === 'image') {
      const src = String(nextNode.attrs?.src || '');
      if (isDataImageUrl(src)) {
        const altText = String(nextNode.attrs?.alt || '').trim();
        const file = await dataUrlToFile(src, altText || `public-page-image-${Date.now()}.png`);
        const uploaded = await boardApi.uploadBoardImage(file);
        const normalizedImageUrl = toAbsoluteImageUrl(uploaded?.url);
        nextNode.attrs = {
          ...(nextNode.attrs || {}),
          src: normalizedImageUrl || src,
          alt: uploaded?.originalFilename || altText || file.name,
        };
      }
    }

    if (Array.isArray(nextNode.content)) {
      nextNode.content = await Promise.all(
        nextNode.content.map((childNode) => processNode(childNode))
      );
    }

    return nextNode;
  };

  return processNode(contentJson);
};

const formatPublishedAt = (value) => {
  if (!value) return '아직 저장되지 않음';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
};

const AdminPublicPages = () => {
  const [selectedType, setSelectedType] = useState('CLUB');
  const [title, setTitle] = useState('');
  const [contentJson, setContentJson] = useState(createEmptyDoc());
  const [publishedAt, setPublishedAt] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const selectedPage = useMemo(
    () => pageOptions.find((page) => page.type === selectedType) || pageOptions[0],
    [selectedType]
  );

  useEffect(() => {
    let ignore = false;

    const loadPage = async () => {
      setLoading(true);
      setMessage('');
      setError('');
      try {
        const page = await getPublicPage(selectedType);
        if (ignore) return;
        setTitle(page?.title || selectedPage.title);
        setContentJson(page?.contentJson || createDocFromText(page?.contentText || ''));
        setPublishedAt(page?.publishedAt || null);
      } catch (loadError) {
        if (ignore) return;
        if (loadError?.status === 404) {
          setTitle(selectedPage.title);
          setContentJson(createEmptyDoc());
          setPublishedAt(null);
          setMessage('아직 작성된 공개 페이지가 없습니다.');
        } else {
          setTitle(selectedPage.title);
          setContentJson(createEmptyDoc());
          setPublishedAt(null);
          setError(loadError?.message || '공개 페이지를 불러오지 못했습니다.');
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    };

    loadPage();

    return () => {
      ignore = true;
    };
  }, [selectedPage.title, selectedType]);

  const handleSave = async () => {
    if (saving) return;

    const normalizedTitle = title.trim();
    if (!normalizedTitle) {
      setError('제목을 입력해주세요.');
      return;
    }

    const contentText = getTextFromJson(contentJson);
    if (!contentText && !containsImageNode(contentJson)) {
      setError('내용을 입력해주세요.');
      return;
    }

    setSaving(true);
    setMessage('');
    setError('');

    try {
      const normalizedContentJson = await replaceBase64ImagesWithUploadedUrls(contentJson);
      const payload = {
        title: normalizedTitle,
        contentFormat: 'TIPTAP_JSON',
        contentJson: normalizedContentJson,
        contentHtml: jsonToHtml(normalizedContentJson),
        contentText: getTextFromJson(normalizedContentJson),
      };

      const savedPage = await savePublicPage(selectedType, payload);
      setTitle(savedPage?.title || normalizedTitle);
      setContentJson(savedPage?.contentJson || normalizedContentJson);
      setPublishedAt(savedPage?.publishedAt || null);
      setMessage('공개 페이지가 저장되었습니다.');
    } catch (saveError) {
      setError(saveError?.message || '공개 페이지 저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const previewHtml = useMemo(() => jsonToHtml(contentJson), [contentJson]);

  return (
    <div className={styles.layout}>
      <AdminSidebar />
      <div className={styles.mainArea}>
        <AdminHeader title="공개 페이지 관리" />
        <main className={styles.contentArea}>
          <div className={styles.shell}>
            <aside className={styles.sidePanel}>
              {pageOptions.map((page) => (
                <button
                  key={page.type}
                  type="button"
                  className={`${styles.pageTab} ${selectedType === page.type ? styles.pageTabActive : ''}`}
                  onClick={() => setSelectedType(page.type)}
                >
                  <span className={styles.tabTitle}>{page.title}</span>
                  <span className={styles.tabDescription}>{page.description}</span>
                </button>
              ))}
            </aside>

            <section>
              <div className={styles.editorPanel}>
                <div className={styles.panelHeader}>
                  <div>
                    <p className={styles.eyebrow}>{selectedType}</p>
                    <h2 className={styles.panelTitle}>{selectedPage.title}</h2>
                    <p className={styles.panelMeta}>
                      마지막 발행: {formatPublishedAt(publishedAt)}
                    </p>
                  </div>
                  <span className={styles.statusBadge}>
                    {loading ? '불러오는 중' : '게시판 비노출 고정 페이지'}
                  </span>
                </div>

                {message && (
                  <p className={`${styles.message} ${styles.messageInfo}`}>{message}</p>
                )}
                {error && (
                  <p className={`${styles.message} ${styles.messageError}`}>{error}</p>
                )}

                <div className={styles.fieldGroup}>
                  <label className={styles.label} htmlFor="public-page-title">
                    제목
                  </label>
                  <input
                    id="public-page-title"
                    className={styles.input}
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    disabled={loading || saving}
                    placeholder="외부 페이지 제목"
                  />
                </div>

                <div className={styles.fieldGroup}>
                  <span className={styles.label}>내용</span>
                  <div className={styles.editorWrap}>
                    <RichTextEditor
                      value={contentJson}
                      onChange={setContentJson}
                      editable={!loading && !saving}
                      placeholder={`${selectedPage.title} 내용을 입력해주세요.`}
                      onUploadImage={boardApi.uploadBoardImage}
                    />
                  </div>
                </div>

                <div className={styles.actionRow}>
                  <button
                    type="button"
                    className={styles.secondaryButton}
                    onClick={() => setContentJson(createEmptyDoc())}
                    disabled={loading || saving}
                  >
                    내용 비우기
                  </button>
                  <button
                    type="button"
                    className={styles.primaryButton}
                    onClick={handleSave}
                    disabled={loading || saving}
                  >
                    {saving ? '저장 중...' : '공개 페이지 저장'}
                  </button>
                </div>
              </div>

              <div className={styles.previewPanel}>
                <h3 className={styles.previewTitle}>미리보기</h3>
                <div
                  className={styles.previewBody}
                  dangerouslySetInnerHTML={{ __html: previewHtml }}
                />
              </div>
            </section>
          </div>
        </main>
      </div>
    </div>
  );
};

export default AdminPublicPages;
