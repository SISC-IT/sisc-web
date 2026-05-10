import { useEffect, useRef, useState } from 'react';
import { EditorContent, NodeViewWrapper, ReactNodeViewRenderer, useEditor } from '@tiptap/react';
import { Extension } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import Highlight from '@tiptap/extension-highlight';
import { TextStyle } from '@tiptap/extension-text-style';
import { Color } from '@tiptap/extension-color';
import Placeholder from '@tiptap/extension-placeholder';
import Underline from '@tiptap/extension-underline';
import TextAlign from '@tiptap/extension-text-align';
import styles from './RichTextEditor.module.css';

const isDataImageUrl = (value = '') => /^data:image\//i.test(String(value || '').trim());
const DEFAULT_IMAGE_ORIGIN = 'https://api.sisc.kr';

const resolveImageOrigin = () => {
  const configuredApiUrl = String(import.meta.env.VITE_API_URL || '').trim();
  if (!configuredApiUrl) return DEFAULT_IMAGE_ORIGIN;

  try {
    return new URL(configuredApiUrl).origin;
  } catch {
    return DEFAULT_IMAGE_ORIGIN;
  }
};

const API_IMAGE_ORIGIN = resolveImageOrigin();

const toAbsoluteImageUrl = (value = '') => {
  const src = String(value || '').trim();
  if (!src) return '';
  if (/^data:/i.test(src) || /^blob:/i.test(src)) {
    return src;
  }

  let normalizedPath = src;

  if (/^(https?:)?\/\//i.test(src)) {
    try {
      normalizedPath = new URL(src, API_IMAGE_ORIGIN).pathname || src;
    } catch {
      normalizedPath = src;
    }
  }

  if (!normalizedPath.startsWith('/')) {
    normalizedPath = `/${normalizedPath.replace(/^\/+/, '')}`;
  }

  const uploadsImagesIndex = normalizedPath.indexOf('/uploads/images/');
  if (uploadsImagesIndex >= 0) {
    normalizedPath = normalizedPath.slice(uploadsImagesIndex);
  }

  return `${API_IMAGE_ORIGIN}${normalizedPath}`;
};

const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'heic', 'heif'];

const isImageFile = (file) => {
  if (!file) return false;

  const mimeType = String(file.type || '').toLowerCase();
  if (mimeType.startsWith('image/')) {
    return true;
  }

  const filename = String(file.name || '').toLowerCase();
  const extension = filename.includes('.') ? filename.split('.').pop() : '';
  return IMAGE_EXTENSIONS.includes(extension);
};

const dataUrlToFile = async (dataUrl, filename = 'pasted-image.png') => {
  const response = await fetch(dataUrl);
  const blob = await response.blob();
  const extension = blob.type?.split('/')[1] || 'png';
  const normalizedName = filename.includes('.') ? filename : `${filename}.${extension}`;
  return new File([blob], normalizedName, { type: blob.type || 'image/png' });
};

const blobToFile = (blob, filename = 'pasted-image.png') => {
  const extension = blob.type?.split('/')[1] || 'png';
  const normalizedName = filename.includes('.') ? filename : `${filename}.${extension}`;
  return new File([blob], normalizedName, { type: blob.type || 'image/png' });
};

const remoteUrlToFile = async (url, filename = 'pasted-image.png') => {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch image: ${response.status}`);
  }
  const blob = await response.blob();
  return blobToFile(blob, filename);
};

const extractImageSrcsFromHtml = (html = '') => {
  if (!html) return [];

  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    return Array.from(doc.querySelectorAll('img'))
      .map((img) => String(img.getAttribute('src') || '').trim())
      .filter(Boolean);
  } catch {
    return [];
  }
};

const hasClipboardImage = async () => {
  if (!navigator.clipboard?.read) return false;

  try {
    const clipboard = await navigator.clipboard.read();
    return clipboard.some((item) => item.types.some((type) => type.startsWith('image/')));
  } catch {
    return false;
  }
};

const FONT_FAMILY_OPTIONS = [
  { label: '기본', value: '' },
  { label: 'Pretendard', value: 'Pretendard' },
  { label: 'Noto Sans KR', value: 'Noto Sans KR' },
  { label: 'Apple SD Gothic Neo', value: 'Apple SD Gothic Neo' },
  { label: 'Arial', value: 'Arial' },
  { label: 'Georgia', value: 'Georgia' },
  { label: 'Courier New', value: 'Courier New' },
];

const FONT_SIZE_OPTIONS = [12, 14, 16, 18, 20, 24, 28, 32, 40];

const FontFamily = Extension.create({
  name: 'fontFamily',

  addGlobalAttributes() {
    return [
      {
        types: ['textStyle'],
        attributes: {
          fontFamily: {
            default: null,
            parseHTML: (element) => {
              const value = element.style.fontFamily || '';
              return value ? value.split(',')[0].replaceAll('"', '').replaceAll("'", '') : null;
            },
            renderHTML: (attributes) => {
              if (!attributes.fontFamily) return {};
              return { style: `font-family: ${attributes.fontFamily}` };
            },
          },
        },
      },
    ];
  },
});

const FontSize = Extension.create({
  name: 'fontSize',

  addGlobalAttributes() {
    return [
      {
        types: ['textStyle'],
        attributes: {
          fontSize: {
            default: null,
            parseHTML: (element) => element.style.fontSize?.replace('px', '') || null,
            renderHTML: (attributes) => {
              if (!attributes.fontSize) return {};
              return { style: `font-size: ${attributes.fontSize}px` };
            },
          },
        },
      },
    ];
  },
});

const MIN_IMAGE_WIDTH = 120;
const MIN_IMAGE_HEIGHT = 80;
const DEFAULT_IMAGE_ALIGN = 'left';
const IMAGE_ALIGN_OPTIONS = [
  { label: '왼쪽', value: 'left' },
  { label: '가운데', value: 'center' },
  { label: '오른쪽', value: 'right' },
];

const ResizableImageNodeView = (props) => {
  const { editor, node, updateAttributes, selected } = props;
  const imageRef = useRef(null);
  const resizeHandlersRef = useRef(null);
  const [isHovered, setIsHovered] = useState(false);
  const [isResizing, setIsResizing] = useState(false);

  const stopResizing = () => {
    const handlers = resizeHandlersRef.current;
    if (handlers) {
      window.removeEventListener('mousemove', handlers.onMove);
      window.removeEventListener('mouseup', handlers.onUp);
      resizeHandlersRef.current = null;
    }

    setIsResizing(false);
  };

  useEffect(() => {
    return () => {
      stopResizing();
    };
  }, []);

  const startResizing = (event) => {
    if (!editor?.isEditable) return;

    event.preventDefault();
    event.stopPropagation();

    const imageElement = imageRef.current;
    if (!imageElement) return;

    const rect = imageElement.getBoundingClientRect();
    const startWidth = rect.width || MIN_IMAGE_WIDTH;
    const startHeight = rect.height || MIN_IMAGE_HEIGHT;
    const startX = event.clientX;
    const startY = event.clientY;
    const direction = String(event.currentTarget?.dataset?.direction || 'se');

    const isHorizontalOnly = direction === 'e';
    const isVerticalOnly = direction === 's';
    const isCorner = direction === 'se';

    

    const onMove = (moveEvent) => {
      const xDelta = moveEvent.clientX - startX;
      const yDelta = moveEvent.clientY - startY;

      if (isHorizontalOnly) {
        const nextWidth = Math.max(MIN_IMAGE_WIDTH, Math.round(startWidth + xDelta));
        updateAttributes({ width: `${nextWidth}px` });
        return;
      }

      if (isVerticalOnly) {
        const nextHeight = Math.max(MIN_IMAGE_HEIGHT, Math.round(startHeight + yDelta));
        updateAttributes({ height: `${nextHeight}px` });
        return;
      }

      if (isCorner) {
        // proportional scaling: base on x movement and apply to both axes
        const nextWidth = Math.max(MIN_IMAGE_WIDTH, Math.round(startWidth + xDelta));
        const scale = nextWidth / Math.max(1, startWidth);
        const nextHeight = Math.max(MIN_IMAGE_HEIGHT, Math.round(startHeight * scale));
        updateAttributes({ width: `${nextWidth}px`, height: `${nextHeight}px` });
      }
    };

    const onUp = () => {
      stopResizing();
    };

    resizeHandlersRef.current = { onMove, onUp };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    setIsResizing(true);
  };

  const width = String(node.attrs?.width || '').trim();
  const align = String(node.attrs?.align || DEFAULT_IMAGE_ALIGN);
  const shouldShowHandle = editor?.isEditable && (isHovered || isResizing || selected);

  const frameStyle = {
    marginLeft: align === 'right' ? 'auto' : align === 'center' ? 'auto' : '0',
    marginRight: align === 'left' ? 'auto' : align === 'center' ? 'auto' : '0',
  };

  return (
    <NodeViewWrapper
      as="div"
      className={styles.imageNodeWrapper}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div className={styles.imageFrame} style={frameStyle}>
        <img
          ref={imageRef}
          src={node.attrs.src}
          alt={node.attrs.alt || ''}
          className={styles.resizableImage}
          style={
            width || node.attrs?.height
              ? { ...(width ? { width } : {}), ...(node.attrs?.height ? { height: node.attrs.height } : {}) }
              : undefined
          }
          draggable={false}
        />

        {shouldShowHandle ? (
          <>
            {['e', 's'].map((direction) => (
              <button
                key={direction}
                type="button"
                className={`${styles.imageResizeHandle} ${styles[`imageResizeHandle${direction.toUpperCase()}`]}`}
                data-direction={direction}
                onMouseDown={startResizing}
                aria-label={`이미지 크기 조절 ${direction}`}
              />
            ))}

            {['se'].map((direction) => (
              <button
                key={direction}
                type="button"
                className={`${styles.imageResizeHandle} ${styles[`imageResizeHandle${direction.toUpperCase()}`]}`}
                data-direction={direction}
                onMouseDown={startResizing}
                aria-label={`이미지 크기 조절 ${direction}`}
              />
            ))}
          </>
        ) : null}
      </div>
    </NodeViewWrapper>
  );
};

const ResizableImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null,
        parseHTML: (element) => element.getAttribute('width') || element.style.width || null,
        renderHTML: (attributes) => {
          if (!attributes.width) return {};
          return {
            width: attributes.width,
            style: `width: ${attributes.width}; ${attributes.height ? `height: ${attributes.height};` : 'height: auto;'}`,
          };
        },
      },
      height: {
        default: null,
        parseHTML: (element) => element.getAttribute('height') || element.style.height || null,
        renderHTML: (attributes) => {
          if (!attributes.height) return {};
          return {
            height: attributes.height,
            style: `height: ${attributes.height}; ${attributes.width ? `width: ${attributes.width};` : ''}`,
          };
        },
      },
      align: {
        default: DEFAULT_IMAGE_ALIGN,
        parseHTML: (element) => element.getAttribute('data-align') || DEFAULT_IMAGE_ALIGN,
        renderHTML: (attributes) => {
          const align = String(attributes.align || DEFAULT_IMAGE_ALIGN);
          if (align === 'center') {
            return { 'data-align': 'center', style: 'display: block; margin-left: auto; margin-right: auto;' };
          }

          if (align === 'right') {
            return { 'data-align': 'right', style: 'display: block; margin-left: auto; margin-right: 0;' };
          }

          return { 'data-align': 'left', style: 'display: block; margin-left: 0; margin-right: auto;' };
        },
      },
    };
  },

  addNodeView() {
    return ReactNodeViewRenderer(ResizableImageNodeView);
  },
});

const createDoc = (content = []) => ({
  type: 'doc',
  content: Array.isArray(content) ? content : [],
});

const normalizeContent = (value) => {
  if (!value) {
    return createDoc([
      {
        type: 'paragraph',
        content: [{ type: 'text', text: '' }],
      },
    ]);
  }

  if (typeof value === 'string') {
    return createDoc([
      {
        type: 'paragraph',
        content: value.trim()
          ? [{ type: 'text', text: value }]
          : [{ type: 'text', text: '' }],
      },
    ]);
  }

  if (value.type === 'doc' && Array.isArray(value.content)) {
    return value;
  }

  return createDoc([
    {
      type: 'paragraph',
      content: [{ type: 'text', text: '' }],
    },
  ]);
};

const RichTextEditor = ({
  value,
  onChange,
  editable = true,
  placeholder = '내용을 입력해주세요.',
  onUploadImage,
  onImageInserted,
}) => {
  const onChangeRef = useRef(onChange);
  const imageInputRef = useRef(null);
  const [isUploadingImage, setIsUploadingImage] = useState(false);

  

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const editor = useEditor({
    extensions: [
      StarterKit,
      Underline,
      TextStyle,
      FontFamily,
      FontSize,
      Color,
      Highlight.configure({ multicolor: true }),
      ResizableImage.configure({
        inline: false,
        allowBase64: false,
      }),
      Link.configure({
        openOnClick: false,
        autolink: true,
        linkOnPaste: true,
      }),
      TextAlign.configure({
        types: ['heading', 'paragraph'],
      }),
      Placeholder.configure({ placeholder }),
    ],
    content: normalizeContent(value),
    editable,
    immediatelyRender: false,
    onUpdate: ({ editor: currentEditor }) => {
      const nextJson = currentEditor.getJSON();
      console.log('editor content', nextJson);
      onChangeRef.current?.(nextJson);
    },
    editorProps: {
      handlePaste: (_view, event) => {
        if (!editable || !onUploadImage) return false;

        const clipboardFiles = Array.from(event.clipboardData?.files || []).filter(isImageFile);
        const clipboardItems = Array.from(event.clipboardData?.items || []);
        const plainText = String(event.clipboardData?.getData('text/plain') || '').trim();
        const isImageFilenameText = /\.(png|jpe?g|gif|webp|bmp|heic|heif)$/i.test(plainText);
        const pastedHtml = event.clipboardData?.getData('text/html') || '';

        let imageFiles = clipboardItems
          .filter((item) => item.type.startsWith('image/') || item.kind === 'file')
          .map((item) => item.getAsFile())
          .filter(isImageFile);

        if (imageFiles.length === 0 && clipboardFiles.length > 0) {
          imageFiles = clipboardFiles;
        }

        const hasImageLikePayload =
          imageFiles.length > 0 ||
          clipboardFiles.length > 0 ||
          clipboardItems.some((item) => item.type.startsWith('image/'));

        const hasUnknownFilePayload =
          clipboardItems.some((item) => item.kind === 'file') ||
          (event.clipboardData?.files?.length || 0) > 0;

        const shouldTryNavigatorClipboardFallback =
          !!navigator.clipboard?.read &&
          !hasImageLikePayload &&
          !hasUnknownFilePayload &&
          !pastedHtml &&
          (!plainText || isImageFilenameText);

        if (!hasImageLikePayload && !(hasUnknownFilePayload && isImageFilenameText) && !shouldTryNavigatorClipboardFallback) {
          return false;
        }

        const htmlImageSrcs = extractImageSrcsFromHtml(pastedHtml);
        const hasHtmlImage = htmlImageSrcs.length > 0;

        const hasHandledImage = imageFiles.length > 0 || hasHtmlImage;

        if (!hasHandledImage) {
          // Prevent fallback plain-text insertion like "xxx.png" even when MIME is missing.
          event.preventDefault();

          // Windows 일부 환경에서는 paste event에 파일이 없고 navigator.clipboard.read()에만 이미지가 있음.
          if (navigator.clipboard?.read) {
            window.setTimeout(() => {
              Promise.resolve()
                .then(async () => {
                  const canReadImage = await hasClipboardImage();
                  if (!canReadImage) return;

                  setIsUploadingImage(true);
                  const clipboard = await navigator.clipboard.read();
                  for (const item of clipboard) {
                    const imageType = item.types.find((type) => type.startsWith('image/'));
                    if (!imageType) continue;

                    const blob = await item.getType(imageType);
                    const file = blobToFile(blob, `pasted-image-${Date.now()}.png`);
                    await insertImage(file);
                  }
                })
                .catch((error) => {
                  console.error('clipboard.read 이미지 붙여넣기 실패:', error);
                })
                .finally(() => {
                  setIsUploadingImage(false);
                });
            }, 0);
          }

          return true;
        }

        event.preventDefault();

        window.setTimeout(() => {
          Promise.resolve()
            .then(async () => {
              setIsUploadingImage(true);
              const resolvedFiles = await Promise.all(imageFiles);
              for (const file of resolvedFiles) {
                await insertImage(file);
              }

              if (htmlImageSrcs.length > 0) {
                for (let index = 0; index < htmlImageSrcs.length; index += 1) {
                  const src = htmlImageSrcs[index];
                  try {
                    if (isDataImageUrl(src)) {
                      const file = await dataUrlToFile(src, `pasted-image-${Date.now()}-${index + 1}.png`);
                      await insertImage(file);
                      continue;
                    }

                    if (/^https?:\/\//i.test(src) || /^blob:/i.test(src)) {
                      const file = await remoteUrlToFile(src, `pasted-image-${Date.now()}-${index + 1}.png`);
                      await insertImage(file);
                    }
                  } catch (error) {
                    console.error('HTML 이미지 src 업로드 실패:', error);
                  }
                }
              }
            })
            .catch((error) => {
              console.error('이미지 붙여넣기 실패:', error);
            })
            .finally(() => {
              setIsUploadingImage(false);
            });
        }, 0);

        return true;
      },
      handleDrop: (_view, event) => {
        if (!editable || !onUploadImage) return false;

        const droppedFiles = Array.from(event.dataTransfer?.files || []).filter(isImageFile);

        if ((event.dataTransfer?.files?.length || 0) > 0 && droppedFiles.length === 0) {
          // Block filename text insertion when file metadata has empty MIME.
          event.preventDefault();
          return true;
        }

        if (droppedFiles.length === 0) {
          return false;
        }

        event.preventDefault();

        window.setTimeout(() => {
          Promise.resolve()
            .then(async () => {
              setIsUploadingImage(true);
              for (const file of droppedFiles) {
                await insertImage(file);
              }
            })
            .catch((error) => {
              console.error('이미지 드래그 앤 드롭 실패:', error);
            })
            .finally(() => {
              setIsUploadingImage(false);
            });
        }, 0);

        return true;
      },
    },
  });

  const insertImage = async (file) => {
    if (!file || !onUploadImage || !editor) return;

    const uploadedImage = await onUploadImage(file);
    const normalizedImageUrl = toAbsoluteImageUrl(uploadedImage?.url);
    if (!normalizedImageUrl) return;

    editor.chain().focus().setImage({
      src: normalizedImageUrl,
      alt: uploadedImage.originalFilename || file.name || '',
    }).run();

    onImageInserted?.({
      ...uploadedImage,
      url: normalizedImageUrl,
    });
  };

  const handleImageInputChange = async (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    try {
      setIsUploadingImage(true);
      for (const file of files) {
        // Insert images one by one so each lands at the current cursor position.
        await insertImage(file);
      }
    } finally {
      setIsUploadingImage(false);
      event.target.value = '';
    }
  };

  const promptLink = () => {
    if (!editor || !editable) return;

    const previousUrl = editor.getAttributes('link').href || '';
    const nextUrl = window.prompt('링크 주소를 입력하세요.', previousUrl);

    if (nextUrl === null) return;

    const trimmedUrl = nextUrl.trim();

    if (!trimmedUrl) {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }

    editor.chain().focus().extendMarkRange('link').setLink({ href: trimmedUrl }).run();
  };

  const clearFormatting = () => {
    if (!editor || !editable) return;

    editor.chain().focus().unsetAllMarks().clearNodes().setParagraph().run();
  };

  const applyTextColor = (color) => {
    if (!editor || !editable) return;

    if (!color) {
      editor.chain().focus().unsetColor().run();
      return;
    }

    editor.chain().focus().setColor(color).run();
  };

  const applyFontFamily = (fontFamily) => {
    if (!editor || !editable) return;

    editor.chain().focus().setMark('textStyle', { fontFamily }).run();
  };

  const applyFontSize = (fontSize) => {
    if (!editor || !editable) return;

    if (!fontSize) {
      editor.chain().focus().setMark('textStyle', { fontSize: null }).run();
      return;
    }

    editor.chain().focus().setMark('textStyle', { fontSize: String(fontSize) }).run();
  };

  const applyImageWidth = (width) => {
    if (!editor || !editable || !editor.isActive('image')) return;

    const nextWidth = width ? String(width) : null;
    editor.chain().focus().updateAttributes('image', { width: nextWidth }).run();
  };

  const applyImageAlignment = (align) => {
    if (!editor || !editable || !editor.isActive('image')) return;

    editor.chain().focus().updateAttributes('image', { align }).run();
  };

  const resetSelectedImageSize = () => {
    if (!editor || !editable || !editor.isActive('image')) return;

    editor.chain().focus().updateAttributes('image', { width: null }).run();
  };

  const deleteSelectedImage = () => {
    if (!editor || !editable || !editor.isActive('image')) return;

    editor.chain().focus().deleteSelection().run();
  };

  const currentImageAlignment = editor?.isActive('image') ? editor.getAttributes('image')?.align || DEFAULT_IMAGE_ALIGN : DEFAULT_IMAGE_ALIGN;
  const currentImageWidth = editor?.isActive('image') ? editor.getAttributes('image')?.width || '' : '';

  useEffect(() => {
    if (!editor) return;

    editor.setEditable(editable);
  }, [editor, editable]);

  useEffect(() => {
    if (!editor) return;

    const nextContent = normalizeContent(value);
    const currentContent = editor.getJSON();
    if (JSON.stringify(currentContent) === JSON.stringify(nextContent)) {
      return;
    }

    editor.commands.setContent(nextContent, false);
  }, [editor, value]);

  if (!editor) {
    return <div className={styles.editorShell} />;
  }

  return (
    <div className={styles.editorShell}>
      <div className={styles.toolbar}>
        <div className={styles.toolbarGroup}>
          <button type="button" onClick={() => editor.chain().focus().undo().run()} disabled={!editable || !editor.can().chain().focus().undo().run()}>
            ↺
          </button>
          <button type="button" onClick={() => editor.chain().focus().redo().run()} disabled={!editable || !editor.can().chain().focus().redo().run()}>
            ↻
          </button>
        </div>

        <div className={styles.toolbarDivider} />

        <div className={styles.toolbarGroup}>
          <select
            className={styles.toolbarSelect}
            value={editor.isActive('heading', { level: 1 }) ? 'h1' : editor.isActive('heading', { level: 2 }) ? 'h2' : 'p'}
            onChange={(event) => {
              const nextValue = event.target.value;
              if (nextValue === 'h1') {
                editor.chain().focus().toggleHeading({ level: 1 }).run();
                return;
              }
              if (nextValue === 'h2') {
                editor.chain().focus().toggleHeading({ level: 2 }).run();
                return;
              }
              editor.chain().focus().setParagraph().run();
            }}
          >
            <option value="p">본문</option>
            <option value="h1">제목 1</option>
            <option value="h2">제목 2</option>
          </select>

          <select
            className={styles.toolbarSelect}
            value={FONT_SIZE_OPTIONS.includes(Number(editor.getAttributes('textStyle')?.fontSize)) ? Number(editor.getAttributes('textStyle')?.fontSize) : ''}
            onChange={(event) => applyFontSize(event.target.value ? Number(event.target.value) : null)}
          >
            <option value="">크기</option>
            {FONT_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}px
              </option>
            ))}
          </select>

          <select
            className={styles.toolbarSelect}
            value={editor.getAttributes('textStyle')?.fontFamily || ''}
            onChange={(event) => applyFontFamily(event.target.value)}
          >
            {FONT_FAMILY_OPTIONS.map((font) => (
              <option key={font.label} value={font.value} style={{ fontFamily: font.value || undefined }}>
                {font.label}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.toolbarDivider} />

        <div className={styles.toolbarGroup}>
          <button type="button" onClick={() => editor.chain().focus().toggleBold().run()} className={editor.isActive('bold') ? styles.activeButton : ''} aria-label="굵게">
            <strong>B</strong>
          </button>
          <button type="button" onClick={() => editor.chain().focus().toggleItalic().run()} className={editor.isActive('italic') ? styles.activeButton : ''} aria-label="기울임">
            <em style={{fontStyle: 'italic'}}>I</em>
          </button>
          <button type="button" onClick={() => editor.chain().focus().toggleUnderline().run()} className={editor.isActive('underline') ? styles.activeButton : ''} aria-label="밑줄">
            <span style={{textDecoration: 'underline'}}>U</span>
          </button>
          <button type="button" onClick={() => editor.chain().focus().toggleStrike().run()} className={editor.isActive('strike') ? styles.activeButton : ''} aria-label="취소선">
            <span style={{textDecoration: 'line-through'}}>S</span>
          </button>
          <button type="button" onClick={() => editor.chain().focus().toggleHighlight().run()} className={editor.isActive('highlight') ? styles.activeButton : ''} aria-label="형광">
            <span className={styles.highlightLabel}>형광</span>
          </button>
        </div>

        <div className={styles.toolbarDivider} />

        <div className={styles.toolbarGroup}>
          <button type="button" onClick={() => editor.chain().focus().toggleBulletList().run()} className={editor.isActive('bulletList') ? styles.activeButton : ''}>
            •
          </button>
          <button type="button" onClick={() => editor.chain().focus().toggleOrderedList().run()} className={editor.isActive('orderedList') ? styles.activeButton : ''}>
            1.
          </button>
          <button type="button" onClick={() => editor.chain().focus().toggleBlockquote().run()} className={editor.isActive('blockquote') ? styles.activeButton : ''}>
            ❝
          </button>
          <button type="button" onClick={() => editor.chain().focus().toggleCodeBlock().run()} className={editor.isActive('codeBlock') ? styles.activeButton : ''}>
            {'</>'}
          </button>
          <button type="button" onClick={() => editor.chain().focus().setHorizontalRule().run()} disabled={!editable}>
            ─
          </button>
          <button type="button" onClick={() => editor.chain().focus().setHardBreak().run()} disabled={!editable}>
            ↵
          </button>
        </div>

        <div className={styles.toolbarDivider} />

        <div className={styles.toolbarGroup}>
          <button type="button" onClick={() => editor.chain().focus().setTextAlign('left').run()} className={editor.isActive({ textAlign: 'left' }) ? styles.activeButton : ''} aria-label="왼쪽 정렬">
            <span className={styles.alignIcon} data-align="left">
              <span className={styles.alignLine} />
              <span className={styles.alignLine} />
              <span className={styles.alignLine} />
            </span>
          </button>
          <button type="button" onClick={() => editor.chain().focus().setTextAlign('center').run()} className={editor.isActive({ textAlign: 'center' }) ? styles.activeButton : ''} aria-label="가운데 정렬">
            <span className={styles.alignIcon} data-align="center">
              <span className={styles.alignLine} />
              <span className={styles.alignLine} />
              <span className={styles.alignLine} />
            </span>
          </button>
          <button type="button" onClick={() => editor.chain().focus().setTextAlign('right').run()} className={editor.isActive({ textAlign: 'right' }) ? styles.activeButton : ''} aria-label="오른쪽 정렬">
            <span className={styles.alignIcon} data-align="right">
              <span className={styles.alignLine} />
              <span className={styles.alignLine} />
              <span className={styles.alignLine} />
            </span>
          </button>
        </div>

        <div className={styles.toolbarDivider} />

        <div className={styles.toolbarGroup}>
          <button type="button" onClick={promptLink} disabled={!editable} className={editor.isActive('link') ? styles.activeButton : ''}>
            링크
          </button>
          <button type="button" onClick={clearFormatting} disabled={!editable}>
            서식삭제
          </button>
        </div>

        <div className={styles.toolbarDivider} />

        <div className={styles.toolbarGroup}>
          {/* Compact color picker for text color */}
          <input
            type="color"
            className={styles.colorInput}
            value={editor?.getAttributes('textStyle')?.color || '#222222'}
            onChange={(e) => applyTextColor(e.target.value)}
            title="글자 색상 선택"
            aria-label="글자 색상 선택"
          />
          <button type="button" onClick={() => applyTextColor(null)} title="기본 색상으로" style={{ marginLeft: 8 }}>
            ↺
          </button>
        </div>

        <div className={styles.toolbarDivider} />

        <div className={styles.toolbarGroup}>
          <button type="button" title="이미지 업로드" onClick={() => imageInputRef.current?.click()} disabled={!editable || isUploadingImage}>
            {isUploadingImage ? '⏳' : '🖼'}
          </button>
          <input
            ref={imageInputRef}
            type="file"
            accept="image/*"
            multiple
            className={styles.hiddenFileInput}
            onChange={handleImageInputChange}
          />
        </div>

        {editor.isActive('image') ? (
          <>
            <div className={styles.toolbarDivider} />

            <div className={styles.toolbarGroup}>
              {IMAGE_ALIGN_OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  title={option.label}
                  onClick={() => applyImageAlignment(option.value)}
                  className={currentImageAlignment === option.value ? styles.activeButton : ''}
                >
                  {option.value === 'left' ? 'L' : option.value === 'center' ? 'C' : 'R'}
                </button>
              ))}
              <button type="button" title="원본 크기" onClick={resetSelectedImageSize} disabled={!currentImageWidth}>
                ↺
              </button>
              <button type="button" title="이미지 삭제" onClick={deleteSelectedImage}>
                🗑
              </button>

              <select
                className={styles.toolbarSelect}
                value={currentImageWidth}
                onChange={(event) => applyImageWidth(event.target.value)}
                aria-label="이미지 크기"
                style={{ marginLeft: 8 }}
              >
                <option value="">크기</option>
                <option value="25%">25%</option>
                <option value="50%">50%</option>
                <option value="75%">75%</option>
                <option value="100%">100%</option>
              </select>
            </div>
          </>
        ) : null}
      </div>
      <div className={styles.editorBody}>
        <EditorContent editor={editor} />
      </div>
    </div>
  );
};

export default RichTextEditor;
