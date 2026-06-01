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
import toolboxImgIcon from '../../assets/toolbox-img-icon.svg';
import toolboxVideoIcon from '../../assets/toolbox-video-icon.svg';
import toolboxFileIcon from '../../assets/toolbox-file-icon.svg';
import toolboxLeftIcon from '../../assets/toolbox-left-icon.svg';
import toolboxMidIcon from '../../assets/toolbox-mid-icon.svg';
import toolboxRightIcon from '../../assets/toolbox-right-icon.svg';
import toolboxLineIcon from '../../assets/toolbox-line-icon.svg';
import toolboxColonIcon from '../../assets/toolbox-colon-icon.svg';
import toolboxTextColorIcon from '../../assets/toolbox-textcolor-icon.svg';
import toolboxTextBgIcon from '../../assets/toolbox-textBackgroundColor-icon.svg';

import {
  isDataImageUrl,
  toAbsoluteImageUrl,
  dataUrlToFile,
  blobToFile,
  remoteUrlToFile,
  extractImageSrcsFromHtml,
  isImageFile,
} from '../../utils/imageUtils';

const hasClipboardImage = async () => {
  if (!navigator.clipboard?.read) return false;

  try {
    const clipboard = await navigator.clipboard.read();
    return clipboard.some((item) => item.types.some((type) => type.startsWith('image/')));
  } catch {
    return false;
  }
};

const DEFAULT_FONT_SIZE = 15;
const FONT_SIZE_OPTIONS = [12, 14, 15, 16, 18, 20, 24, 28, 32, 40];

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
  onUploadFile,
  onUploadVideo,
  onAttachFiles,
}) => {
  const onChangeRef = useRef(onChange);
  const imageInputRef = useRef(null);
  const videoInputRef = useRef(null);
  const fileInputRef = useRef(null);
  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const [, setSelectionTick] = useState(0);

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
        if (!editable) return false;

        const allDropped = Array.from(event.dataTransfer?.files || []);
        if (allDropped.length === 0) return false;

        const imageFiles = allDropped.filter(isImageFile);
        const otherFiles = allDropped.filter((f) => !isImageFile(f));

        // If parent provided `onAttachFiles`, handle images inline and delegate
        // non-image files to the parent so they become attachments.
        if (onAttachFiles) {
          event.preventDefault();
          window.setTimeout(() => {
            Promise.resolve()
              .then(async () => {
                try {
                  if (imageFiles.length > 0 && onUploadImage) {
                    try {
                      setIsUploadingImage(true);
                      for (const file of imageFiles) {
                        await insertImage(file);
                      }
                    } catch (err) {
                      console.error('이미지 드래그 앤 드롭 실패:', err);
                    } finally {
                      setIsUploadingImage(false);
                    }
                  }

                  if (otherFiles.length > 0) {
                    try {
                      await onAttachFiles(otherFiles);
                    } catch (err) {
                      console.error('파일 드롭 처리 실패 (onAttachFiles):', err);
                    }
                  }
                } catch (error) {
                  console.error('드롭 처리 실패:', error);
                }
              })
              .catch((error) => {
                console.error('드롭 처리 실패:', error);
              });
          }, 0);

          return true;
        }

        // We'll handle both images and other files by inserting them into the editor content.
        // Prevent the default only when we actually handle insertion here.
        const willHandle = imageFiles.length > 0 || otherFiles.length > 0;
        if (!willHandle) return false;

        event.preventDefault();

        window.setTimeout(() => {
          Promise.resolve()
            .then(async () => {
              // images: use existing insertImage flow
              if (imageFiles.length > 0 && onUploadImage) {
                try {
                  setIsUploadingImage(true);
                  for (const file of imageFiles) {
                    await insertImage(file);
                  }
                } catch (err) {
                  console.error('이미지 드래그 앤 드롭 실패:', err);
                } finally {
                  setIsUploadingImage(false);
                }
              }

              // non-image files: upload (prefer onUploadFile) and insert file links into editor
              if (otherFiles.length > 0) {
                try {
                  // If a single-file uploader is available
                  if (onUploadFile) {
                    for (const file of otherFiles) {
                      try {
                        const uploaded = await onUploadFile(file);
                        const normalizedUrl = toAbsoluteImageUrl(uploaded?.url || uploaded?.fileUrl || uploaded?.downloadUrl || uploaded?.savedUrl || file.name);
                        insertFileLink(normalizedUrl || '', uploaded?.originalFilename || file.name);
                      } catch (err) {
                        console.error('파일 드롭 업로드 실패 (onUploadFile):', err);
                      }
                    }
                  } else if (onAttachFiles) {
                    // fallback: onAttachFiles may upload and return uploaded metadata array
                    try {
                      const uploadedArr = await onAttachFiles(otherFiles);
                      if (Array.isArray(uploadedArr)) {
                        for (const uploaded of uploadedArr) {
                          const normalizedUrl = toAbsoluteImageUrl(uploaded?.url || uploaded?.fileUrl || uploaded?.downloadUrl || uploaded?.savedUrl || uploaded?.url);
                          insertFileLink(normalizedUrl || '', uploaded?.originalFilename || uploaded?.name || '첨부파일');
                        }
                      }
                    } catch (err) {
                      console.error('파일 드롭 업로드 실패 (onAttachFiles):', err);
                    }
                  }
                } catch (err) {
                  console.error('파일 드롭 처리 실패:', err);
                }
              }
            })
            .catch((error) => {
              console.error('드롭 처리 실패:', error);
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

  const escapeHtml = (str) => {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  };

  const insertFileLink = (url, filename) => {
    if (!editor || !url) return;
    const safeUrl = escapeHtml(url);
    const safeName = escapeHtml(filename || url);
    editor.chain().focus().insertContent(`<p><a href="${safeUrl}" target="_blank" rel="noopener noreferrer">${safeName}</a></p>`).run();
  };

  const handleFileInputChange = async (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    try {
      // If parent wants attachments, delegate files to it
      if (onAttachFiles) {
        try {
          await onAttachFiles(files);
        } catch (err) {
          console.error('파일 업로드 처리 실패 (onAttachFiles):', err);
        }
        return;
      }

      for (const file of files) {
        if (onUploadFile) {
          const uploaded = await onUploadFile(file);
          const normalizedUrl = toAbsoluteImageUrl(uploaded?.url);
          insertFileLink(normalizedUrl || '', uploaded?.originalFilename || file.name);
        }
      }
    } catch (error) {
      console.error('파일 업로드 실패:', error);
    } finally {
      event.target.value = '';
    }
  };

  const handleVideoInputChange = async (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    try {
      // If parent wants attachments, delegate videos to it
      if (onAttachFiles) {
        try {
          await onAttachFiles(files);
        } catch (err) {
          console.error('비디오 업로드 처리 실패 (onAttachFiles):', err);
        }
        return;
      }

      for (const file of files) {
        if (onUploadVideo) {
          const uploaded = await onUploadVideo(file);
          const normalizedUrl = toAbsoluteImageUrl(uploaded?.url);
          insertFileLink(normalizedUrl || '', uploaded?.originalFilename || file.name);
        }
      }
    } catch (error) {
      console.error('비디오 업로드 실패:', error);
    } finally {
      event.target.value = '';
    }
  };

  const applyTextColor = (color) => {
    if (!editor || !editable) return;

    if (!color) {
      editor.chain().focus().unsetColor().run();
      return;
    }

    editor.chain().focus().setColor(color).run();
  };

  const applyTextBackground = (color) => {
    if (!editor || !editable) return;

    if (!color) {
      // prefer to unset Highlight, fallback to clearing textStyle background
      try {
        editor.chain().focus().unsetHighlight().run();
      } catch (error) {
        console.error('배경색 해제 실패:', error);
        try {
          editor.chain().focus().setMark('textStyle', { backgroundColor: null }).run();
        } catch (nestedError) {
          console.error('배경색 해제 실패(textStyle):', nestedError);
        }
      }
      return;
    }

    // prefer Highlight (supports multicolor), fallback to textStyle mark
    try {
      editor.chain().focus().toggleHighlight({ color }).run();
    } catch (error) {
      console.error('배경색 적용 실패:', error);
      try {
        editor.chain().focus().setMark('textStyle', { backgroundColor: color }).run();
      } catch (nestedError) {
        console.error('배경색 적용 실패(textStyle):', nestedError);
      }
    }
  };

  const insertDividerLine = () => {
    if (!editor || !editable) return;

    editor.chain().focus().setHorizontalRule().run();
  };

  const insertQuoteBlock = () => {
    if (!editor || !editable) return;

    editor.chain().focus().toggleBlockquote().run();
  };

  const applyFontSize = (fontSize) => {
    if (!editor || !editable) return;

    if (!fontSize) {
      editor.chain().focus().setMark('textStyle', { fontSize: null }).run();
      return;
    }

    editor.chain().focus().setMark('textStyle', { fontSize: String(fontSize) }).run();
  };

  const currentFontSize = editor?.getAttributes('textStyle')?.fontSize || DEFAULT_FONT_SIZE;

  useEffect(() => {
    if (!editor) return;

    editor.setEditable(editable);
  }, [editor, editable]);

  // Re-render component when selection changes so toolbar reflects
  // formatting of the currently selected text (bold/italic/align/etc.).
  useEffect(() => {
    if (!editor) return undefined;

    const onSelection = () => {
      setSelectionTick((t) => t + 1);
    };

    editor.on('selectionUpdate', onSelection);
    return () => {
      try {
        editor.off('selectionUpdate', onSelection);
      } catch (error) {
        console.error('selectionUpdate 해제 실패:', error);
      }
    };
  }, [editor]);

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
        <div className={styles.mediaToolbar}>
          <button type="button" title="이미지 업로드" onClick={() => imageInputRef.current?.click()} disabled={!editable || isUploadingImage} className={styles.iconTile}>
            <img src={toolboxImgIcon} alt="이미지" className={styles.iconImg} />
            <span className={styles.iconTileLabel}>사진</span>
          </button>
          <input
            ref={imageInputRef}
            type="file"
            accept="image/*"
            multiple
            className={styles.hiddenFileInput}
            onChange={handleImageInputChange}
          />

          <button type="button" title="비디오 업로드" onClick={() => videoInputRef.current?.click()} disabled={!editable} className={styles.iconTile}>
            <img src={toolboxVideoIcon} alt="비디오" className={styles.iconImg} />
            <span className={styles.iconTileLabel}>동영상</span>
          </button>
          <input
            ref={videoInputRef}
            type="file"
            accept="video/*"
            multiple
            className={styles.hiddenFileInput}
            onChange={handleVideoInputChange}
          />

          <button type="button" title="파일 업로드" onClick={() => fileInputRef.current?.click()} disabled={!editable} className={styles.iconTile}>
            <img src={toolboxFileIcon} alt="파일" className={styles.iconImg} />
            <span className={styles.iconTileLabel}>파일</span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            className={styles.hiddenFileInput}
            onChange={handleFileInputChange}
          />

          <button type="button" title="구분선 삽입" onClick={insertDividerLine} disabled={!editable} className={styles.iconTile}>
            <img src={toolboxLineIcon} alt="구분선" className={styles.iconImg} />
            <span className={styles.iconTileLabel}>구분선</span>
          </button>

          <button type="button" title="인용구 삽입" onClick={insertQuoteBlock} disabled={!editable} className={styles.iconTile}>
            <img src={toolboxColonIcon} alt="인용구" className={styles.iconImg} />
            <span className={styles.iconTileLabel}>인용구</span>
          </button>
        </div>

        <div className={styles.formatToolbar}>
          <select
            className={styles.toolbarSelect}
            value={FONT_SIZE_OPTIONS.includes(Number(currentFontSize)) ? Number(currentFontSize) : DEFAULT_FONT_SIZE}
            onChange={(event) => applyFontSize(event.target.value ? Number(event.target.value) : DEFAULT_FONT_SIZE)}
            aria-label="글씨 크기 선택"
          >
            {FONT_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}px
              </option>
            ))}
          </select>

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

          <div className={styles.colorGroup}>
            <label className={styles.colorLabel} title="글자 색상 선택">
              <img src={toolboxTextColorIcon} alt="text color" className={styles.colorAsset} />
              <input
                type="color"
                className={styles.colorInput}
                value={editor?.getAttributes('textStyle')?.color || '#222222'}
                onChange={(e) => applyTextColor(e.target.value)}
                aria-label="글자 색상 선택"
              />
            </label>

            <label className={styles.colorLabel} title="글자 배경색 선택">
              <img src={toolboxTextBgIcon} alt="text background" className={styles.colorAsset} />
              <input
                type="color"
                className={styles.colorInput}
                value={editor?.getAttributes('highlight')?.color || '#ffffff'}
                onChange={(e) => applyTextBackground(e.target.value)}
                aria-label="글자 배경색 선택"
              />
            </label>
          </div>

          <button type="button" onClick={() => editor.chain().focus().setTextAlign('left').run()} className={editor.isActive({ textAlign: 'left' }) ? styles.activeButton : ''} aria-label="왼쪽 정렬">
            <img src={toolboxLeftIcon} alt="왼쪽정렬" className={styles.alignImg} />
          </button>
          <button type="button" onClick={() => editor.chain().focus().setTextAlign('center').run()} className={editor.isActive({ textAlign: 'center' }) ? styles.activeButton : ''} aria-label="가운데 정렬">
            <img src={toolboxMidIcon} alt="가운데정렬" className={styles.alignImg} />
          </button>
          <button type="button" onClick={() => editor.chain().focus().setTextAlign('right').run()} className={editor.isActive({ textAlign: 'right' }) ? styles.activeButton : ''} aria-label="오른쪽 정렬">
            <img src={toolboxRightIcon} alt="오른쪽정렬" className={styles.alignImg} />
          </button>
        </div>
      </div>
      <div className={styles.editorBody}>
        <EditorContent editor={editor} />
      </div>
    </div>
  );
};

export default RichTextEditor;
