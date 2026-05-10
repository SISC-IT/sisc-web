import { useEffect, useRef } from 'react';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import Placeholder from '@tiptap/extension-placeholder';
import Underline from '@tiptap/extension-underline';
import TextAlign from '@tiptap/extension-text-align';
import styles from './RichTextEditor.module.css';

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

const RichTextEditor = ({ value, onChange, editable = true, placeholder = '내용을 입력해주세요.' }) => {
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const editor = useEditor({
    extensions: [
      StarterKit,
      Underline,
      Image.configure({
        inline: false,
        allowBase64: true,
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
      onChangeRef.current?.(currentEditor.getJSON());
    },
  });

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
        <button type="button" onClick={() => editor.chain().focus().toggleBold().run()} className={editor.isActive('bold') ? styles.activeButton : ''}>
          B
        </button>
        <button type="button" onClick={() => editor.chain().focus().toggleItalic().run()} className={editor.isActive('italic') ? styles.activeButton : ''}>
          I
        </button>
        <button type="button" onClick={() => editor.chain().focus().toggleUnderline().run()} className={editor.isActive('underline') ? styles.activeButton : ''}>
          U
        </button>
        <button type="button" onClick={() => editor.chain().focus().setTextAlign('left').run()} className={editor.isActive({ textAlign: 'left' }) ? styles.activeButton : ''}>
          L
        </button>
        <button type="button" onClick={() => editor.chain().focus().setTextAlign('center').run()} className={editor.isActive({ textAlign: 'center' }) ? styles.activeButton : ''}>
          C
        </button>
        <button type="button" onClick={() => editor.chain().focus().setTextAlign('right').run()} className={editor.isActive({ textAlign: 'right' }) ? styles.activeButton : ''}>
          R
        </button>
      </div>
      <div className={styles.editorBody}>
        <EditorContent editor={editor} />
      </div>
    </div>
  );
};

export default RichTextEditor;
